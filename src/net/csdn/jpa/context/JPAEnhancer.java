package net.csdn.jpa.context;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import net.csdn.common.settings.Settings;
import net.csdn.enhancers.Enhancer;

import java.io.DataInputStream;
import java.lang.reflect.Modifier;
import java.sql.*;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.newArrayList;
import static net.csdn.common.logging.support.MessageFormat.format;

/**
 * User: WilliamZhu
 * Date: 12-6-26
 * Time: 下午10:11
 */
public class JPAEnhancer extends Enhancer {

    private Settings settings;


    public JPAEnhancer(Settings settings) {
        this.settings = settings;
    }

    public void enhanceThisClass(DataInputStream dataInputStream) throws Exception {
        CtClass ctClass = classPool.makeClass(dataInputStream);

        if (!ctClass.subtypeOf(classPool.get("net.csdn.jpa.model.JPABase"))) {
            return;
        }

        // Enhance only JPA entities
        if (!hasAnnotation(ctClass, "javax.persistence.Entity")) {
            return;
        }
        //自动为Model添加属性，之后可以通过attr的方式获取
        enhanceModelProperties(ctClass);
        enhanceModelMethods(ctClass);

        //done
        ctClass.toClass();

    }

    /*
    涵盖一些基本的类型转换
     */
    private String sqlTypeToJavaType(String sqlType) {
        if (newArrayList("CHAR", "VARCHAR", "TEXT").contains(sqlType)) {
            return "String";
        }
        if ("INT".equals(sqlType)) return "int";
        if ("BIGINT".equals(sqlType)) return "long";
        if ("FLOAT".equals(sqlType)) return "float";
        if (newArrayList("DATE", "DATETIME", "TIMESTAMP").contains(sqlType)) return "java.util.Date";
        return "String";

    }

    private void enhanceModelProperties(CtClass ctClass) throws Exception {
        //连接数据库，自动获取所有信息，然后添加属性
        Connection conn = null;
        String entitySimpleName = ctClass.getSimpleName();
        String entityName = ctClass.getName();
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();

            Map<String, Settings> groups = settings.getGroups("datasources");
            Settings mysqlSetting = groups.get("mysql");
            String url = "jdbc:mysql://{}:{}/{}?useUnicode=true&characterEncoding=utf8";
            url = format(url, mysqlSetting.get("host", "127.0.0.1"), mysqlSetting.get("port", "3306"), mysqlSetting.get("database", "csdn_search_client"));
            conn = DriverManager.getConnection(url, mysqlSetting.get("username"), mysqlSetting.get("password"));
            PreparedStatement ps = conn.prepareStatement("select * from " + entitySimpleName + " limit 1");
            ResultSet rs = ps.executeQuery();
            ResultSetMetaData rsme = rs.getMetaData();
            int columnCount = rsme.getColumnCount();
            for (int i = 1; i < columnCount; i++) {
                String fieldName = rsme.getColumnName(i);
                //对定义过的属性略过
                boolean pass = true;
                try {
                    ctClass.getField(fieldName);
                } catch (Exception e) {
                    pass = false;
                }
                if (pass) continue;

                ConstPool constPool = ctClass.getClassFile().getConstPool();
                AnnotationsAttribute attr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
                javassist.bytecode.annotation.Annotation annot = new javassist.bytecode.annotation.Annotation(fieldName.equals("id") ? "javax.persistence.Id" : "javax.persistence.Column", constPool);
                attr.addAnnotation(annot);

                CtField ctField = CtField.make(" public " + sqlTypeToJavaType(rsme.getColumnTypeName(i)) + " " + fieldName + " ;", ctClass);
                ctField.getFieldInfo().addAttribute(attr);
                ctClass.addField(ctField);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null)
                try {
                    conn.close();
                } catch (SQLException e) {

                }
        }

        //hibernate 可能需要 setter/getter 方法，好吧 我们为它添加这些方法

        for (CtField ctField : ctClass.getDeclaredFields()) {

            // Property name
            String propertyName = ctField.getName().substring(0, 1).toUpperCase() + ctField.getName().substring(1);
            String getter = "get" + propertyName;
            String setter = "set" + propertyName;

            try {
                CtMethod ctMethod = ctClass.getDeclaredMethod(getter);
                if (ctMethod.getParameterTypes().length > 0 || Modifier.isStatic(ctMethod.getModifiers())) {
                    throw new NotFoundException("it's not a getter !");
                }
            } catch (NotFoundException noGetter) {

                // Créé le getter
                String code = "public " + ctField.getType().getName() + " " + getter + "() { return this." + ctField.getName() + "; }";
                CtMethod getMethod = CtMethod.make(code, ctClass);
                getMethod.setModifiers(getMethod.getModifiers() | AccessFlag.SYNTHETIC);
                ctClass.addMethod(getMethod);
            }

            if (!isFinal(ctField)) {
                try {
                    CtMethod ctMethod = ctClass.getDeclaredMethod(setter);
                    if (ctMethod.getParameterTypes().length != 1 || !ctMethod.getParameterTypes()[0].equals(ctField.getType()) || Modifier.isStatic(ctMethod.getModifiers())) {
                        throw new NotFoundException("it's not a setter !");
                    }
                } catch (NotFoundException noSetter) {
                    CtMethod setMethod = CtMethod.make("public void " + setter + "(" + ctField.getType().getName() + " value) { this." + ctField.getName() + " = value; }", ctClass);
                    setMethod.setModifiers(setMethod.getModifiers() | AccessFlag.SYNTHETIC);
                    ctClass.addMethod(setMethod);
                }
            }

        }
        ctClass.defrost();

    }


    boolean isFinal(CtField ctField) {
        return Modifier.isFinal(ctField.getModifiers());
    }

    private void enhanceModelMethods(CtClass ctClass) throws Exception {
        String entityName = ctClass.getName();
        String simpleEntityName = ctClass.getSimpleName();

// count
        CtMethod count = CtMethod.make("public static long count() { return getJPAContext().jpql().count(\"" + entityName + "\"); }", ctClass);
        ctClass.addMethod(count);

// count2
        CtMethod count2 = CtMethod.make("public static long count(String query, Object[] params) { return  getJPAContext().jpql().count(\"" + entityName + "\", query, params); }", ctClass);
        ctClass.addMethod(count2);

// findAll
        CtMethod findAll = CtMethod.make("public static java.util.List findAll() { return  getJPAContext().jpql().findAll(\"" + entityName + "\"); }", ctClass);
        ctClass.addMethod(findAll);

// findById
        CtMethod findById = CtMethod.make("public static net.csdn.jpa.model.JPABase findById(Object id) { return  getJPAContext().jpql().findById(" + entityName + ".class, id); }", ctClass);
        ctClass.addMethod(findById);

// find
        CtMethod find = CtMethod.make("public static net.csdn.jpa.model.GenericModel.JPAQuery find(String query, Object[] params) { return  getJPAContext().jpql().find(\"" + entityName + "\", query, params); }", ctClass);
        ctClass.addMethod(find);

// find
        CtMethod find2 = CtMethod.make("public static net.csdn.jpa.model.GenericModel.JPAQuery find() { return  getJPAContext().jpql().find(\"" + entityName + "\"); }", ctClass);
        ctClass.addMethod(find2);

// all
        CtMethod all = CtMethod.make("public static net.csdn.jpa.model.GenericModel.JPAQuery all() { return  getJPAContext().jpql().all(\"" + entityName + "\"); }", ctClass);
        ctClass.addMethod(all);

// delete
        CtMethod delete = CtMethod.make("public static int delete(String query, Object[] params) { return  getJPAContext().jpql().delete(\"" + entityName + "\", query, params); }", ctClass);
        ctClass.addMethod(delete);

// deleteAll
        CtMethod deleteAll = CtMethod.make("public static int deleteAll() { return  getJPAContext().jpql().deleteAll(\"" + entityName + "\"); }", ctClass);
        ctClass.addMethod(deleteAll);

// findOneBy
        CtMethod findOneBy = CtMethod.make("public static net.csdn.jpa.model.JPABase findOneBy(String query, Object[] params) { return  getJPAContext().jpql().findOneBy(\"" + entityName + "\", query, params); }", ctClass);
        ctClass.addMethod(findOneBy);

// create
        CtMethod create = CtMethod.make("public static net.csdn.jpa.model.JPABase create(java.util.Map params) { return  getJPAContext().jpql().create(" + entityName + ".class, params); }", ctClass);
        ctClass.addMethod(create);

// where
        CtMethod where = CtMethod.make("public static net.csdn.jpa.model.JPQL where(String cc){return getJPAContext().jpql(\"" + simpleEntityName + "\").where(cc);}", ctClass);
        ctClass.addMethod(where);

// where2
        CtMethod where2 = CtMethod.make("public static net.csdn.jpa.model.JPQL where(String cc,java.util.Map params){return getJPAContext().jpql(\"" + simpleEntityName + "\").where(cc,params);}", ctClass);
        ctClass.addMethod(where2);

// select
        CtMethod select = CtMethod.make("public static net.csdn.jpa.model.JPQL select(String cc){return getJPAContext().jpql(\"" + simpleEntityName + "\").select(cc);}", ctClass);
        ctClass.addMethod(select);
// joins
        CtMethod joins = CtMethod.make("public static net.csdn.jpa.model.JPQL joins(String cc){return getJPAContext().jpql(\"" + simpleEntityName + "\").joins(cc);}", ctClass);
        ctClass.addMethod(joins);

// order
        CtMethod order = CtMethod.make("public static net.csdn.jpa.model.JPQL order(String cc){return getJPAContext().jpql(\"" + simpleEntityName + "\").order(cc);}", ctClass);
        ctClass.addMethod(order);
// limit
        CtMethod limit = CtMethod.make("public static net.csdn.jpa.model.JPQL limit(int cc){return getJPAContext().jpql(\"" + simpleEntityName + "\").limit(cc);}", ctClass);
        ctClass.addMethod(limit);
// offset
        CtMethod offset = CtMethod.make("public static net.csdn.jpa.model.JPQL offset(int cc){return getJPAContext().jpql(\"" + simpleEntityName + "\").offset(cc);}", ctClass);
        ctClass.addMethod(offset);


        ctClass.defrost();

    }

}
