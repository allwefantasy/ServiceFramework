package net.csdn.jpa.enhancer;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.BitEnhancer;

import java.lang.reflect.Modifier;
import java.sql.*;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.newArrayList;
import static net.csdn.common.logging.support.MessageFormat.format;

/**
 * User: WilliamZhu
 * Date: 12-7-2
 * Time: 下午8:41
 */
public class PropertyEnhancer implements BitEnhancer {

    private Settings settings;

    public PropertyEnhancer(Settings settings) {
        this.settings = settings;
    }

    @Override
    public void enhance(CtClass ctClass) throws Exception {
        autoInjectProperty(ctClass);
        autoInjectGetSet(ctClass);
    }

    /*
   涵盖一些基本的类型转换
    */
    private String sqlTypeToJavaType(String sqlType) {
        if (newArrayList("CHAR", "VARCHAR", "TEXT").contains(sqlType)) {
            return "String";
        }
        if ("INT".equals(sqlType)) return "Integer";
        if ("BIGINT".equals(sqlType)) return "Long";
        if ("FLOAT".equals(sqlType)) return "Float";
        if (newArrayList("DATE", "DATETIME", "TIMESTAMP").contains(sqlType)) return "java.util.Date";
        return "String";

    }

    private void autoInjectProperty(CtClass ctClass) {
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

                CtField ctField = CtField.make(" public " + sqlTypeToJavaType(rsme.getColumnTypeName(i)) + " " + fieldName + " ;", ctClass);

                if (fieldName.equals("id")) {
                    ConstPool constPool = ctClass.getClassFile().getConstPool();
                    AnnotationsAttribute attr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
                    javassist.bytecode.annotation.Annotation annot = new javassist.bytecode.annotation.Annotation("javax.persistence.Id", constPool);
                    attr.addAnnotation(annot);
                    ctField.getFieldInfo().addAttribute(attr);
                }

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
        ctClass.defrost();
    }

    boolean isFinal(CtField ctField) {
        return Modifier.isFinal(ctField.getModifiers());
    }

    private void autoInjectGetSet(CtClass ctClass) throws Exception {


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
}
