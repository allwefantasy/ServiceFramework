package net.csdn.jpa.enhancer;

import javassist.*;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.MemberValue;
import net.csdn.ServiceFramwork;
import net.csdn.annotation.NotMapping;
import net.csdn.annotation.Validate;
import net.csdn.bootstrap.Bootstrap;
import net.csdn.common.collect.Tuple;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.BitEnhancer;
import net.csdn.jpa.type.DBType;

import javax.persistence.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.newArrayList;
import static net.csdn.common.collections.WowCollections.newHashMap;
import static net.csdn.common.logging.support.MessageFormat.format;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-2
 * Time: 下午8:41
 */
public class PropertyEnhancer implements BitEnhancer {

    private Settings settings;
    private CSLogger logger = Loggers.getLogger(getClass());

    public PropertyEnhancer(Settings settings) {
        this.settings = settings;
    }

    @Override
    public void enhance(CtClass ctClass) throws Exception {
        autoInjectProperty(ctClass);
        autoInjectGetSet(ctClass);
    }

    private static void createAnnotation(AnnotationsAttribute attribute, Class<? extends Annotation> annotationType, Map<String, MemberValue> members) {
        javassist.bytecode.annotation.Annotation annotation = new javassist.bytecode.annotation.Annotation(annotationType.getName(), attribute.getConstPool());
        for (Map.Entry<String, MemberValue> member : members.entrySet()) {
            annotation.addMemberValue(member.getKey(), member.getValue());
        }
        attribute.addAnnotation(annotation);
    }

    private static void createAnnotation(AnnotationsAttribute attribute, Class<? extends Annotation> annotationType) {
        createAnnotation(attribute, annotationType, new HashMap<String, MemberValue>());
    }


    private void notMapping(CtClass ctClass, List<String> skipFields) {
        if (ctClass.hasAnnotation(NotMapping.class)) {
            try {
                NotMapping notMapping = (NotMapping) ctClass.getAnnotation(NotMapping.class);
                for (String str : notMapping.value()) {
                    skipFields.add(str);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        autoNotMapping(ctClass, skipFields);
    }

    //自动过滤掉
    private void autoNotMapping(CtClass ctClass, List<String> skipFields) {
        CtField[] fields = ctClass.getDeclaredFields();
        for (CtField ctField : fields) {
            guessNotMappingName(ctField, ManyToOne.class, skipFields);
            guessNotMappingName(ctField, OneToOne.class, skipFields);
        }
    }

    private void guessNotMappingName(CtField ctField, Class clzz, List<String> skipFields) {
        if (ctField.hasAnnotation(clzz)) {
            Method mappedBy = null;
            try {
                Object wow = ctField.getAnnotation(clzz);
                mappedBy = wow.getClass().getMethod("mappedBy");
                String value = (String) mappedBy.invoke(wow);
                if (value == null || value.isEmpty()) {
                    skipFields.add(ctField.getName() + "_id");
                }
            } catch (Exception e) {
                if (mappedBy == null) {
                    skipFields.add(ctField.getName() + "_id");
                }
            }
        }
    }


    private void autoInjectProperty(CtClass ctClass) {
        //连接数据库，自动获取所有信息，然后添加属性
        Connection conn = null;
        String entitySimpleName = ctClass.getSimpleName();
        List<String> skipFields = newArrayList();

        notMapping(ctClass, skipFields);

        try {
            DBType dbType = ServiceFramwork.injector.getInstance(DBType.class);
            Tuple<ResultSetMetaData, Connection> resultSetMetaDataConnectionTuple = dbType.metaData(entitySimpleName);
            conn = resultSetMetaDataConnectionTuple.v2();
            ResultSetMetaData rsme = resultSetMetaDataConnectionTuple.v1();
            int columnCount = rsme.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                String fieldName = rsme.getColumnName(i);
                if (skipFields.contains(fieldName)) continue;
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
                CtField ctField = CtField.make(" private " + dbType.typeToJava(rsme.getColumnTypeName(i)).v2() + " " + fieldName + " ;", ctClass);

                String fieldType = rsme.getColumnTypeName(i);
                Tuple<Class, Map> tuple = dbType.dateType(fieldType, constPool);
                if (tuple != null) {
                    createAnnotation(attr, tuple.v1(), tuple.v2());
                }

                if (rsme.isAutoIncrement(i) || rsme.getColumnTypeName(i).equals("id")) {
                    createAnnotation(attr, javax.persistence.Id.class, newHashMap());
                    createAnnotation(attr, javax.persistence.GeneratedValue.class, newHashMap());
                }

                if (attr.getAnnotations().length > 0) {
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

    boolean isStatic(CtField ctField) {
        return Modifier.isStatic(ctField.getModifiers());
    }

    private void autoInjectGetSet(CtClass ctClass) throws Exception {


        //hibernate 可能需要 setter/getter 方法，好吧 我们为它添加这些方法

        for (CtField ctField : ctClass.getDeclaredFields()) {
            if (isFinal(ctField) || isStatic(ctField) || ctField.hasAnnotation(Validate.class))
                continue;
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

                String code = "public " + ctField.getType().getName() + " " + getter + "() { return this." + ctField.getName() + "; }";
                CtMethod getMethod = CtMethod.make(code, ctClass);
                getMethod.setModifiers(getMethod.getModifiers() | AccessFlag.SYNTHETIC);
                ctClass.addMethod(getMethod);
            }

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
        ctClass.defrost();

    }
}
