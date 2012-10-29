package net.csdn.jpa.enhancer;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.BooleanMemberValue;
import net.csdn.annotation.validate.Validate;
import net.csdn.common.collect.Tuple;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.BitEnhancer;
import net.csdn.jpa.JPA;
import net.csdn.jpa.type.DBInfo;
import net.csdn.jpa.type.DBType;

import javax.persistence.Column;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.map;
import static net.csdn.common.enhancer.EnhancerHelper.createAnnotation;

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
    public void enhance(List<ModelClass> modelClasses) throws Exception {

        for (ModelClass modelClass : modelClasses) {
            autoInjectProperty(modelClass);
            autoInjectGetSet(modelClass.originClass);
        }

    }


    private void autoInjectProperty(ModelClass modelClass) {

        //连接数据库，自动获取所有信息，然后添加属性
        CtClass ctClass = modelClass.originClass;
        String entitySimpleName = ctClass.getSimpleName();
        List<String> skipFields = modelClass.notMappingColumns();


        try {
            DBType dbType = JPA.injector.getInstance(DBType.class);
            DBInfo dbInfo = JPA.injector.getInstance(DBInfo.class);

            Map<String, String> columns = dbInfo.tableColumns.get(entitySimpleName);
            if (columns == null) return;
            for (String columnName : columns.keySet()) {
                String fieldName = columnName;
                String fieldType = columns.get(columnName);
                if (skipFields.contains(fieldName)) continue;
                if (fieldName.equals("discriminator")) continue;

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
                CtField ctField = CtField.make(" private " + dbType.typeToJava(fieldType).v2() + " " + fieldName + " ;", ctClass);

                Tuple<Class, Map> tuple = dbType.dateType(fieldType, constPool);
                if (tuple != null) {
                    createAnnotation(attr, tuple.v1(), tuple.v2());
                }

                if (fieldName.equals("id")) {
                    createAnnotation(attr, javax.persistence.Id.class, map());
                    createAnnotation(attr, javax.persistence.GeneratedValue.class, map());
                } else {
                    createAnnotation(attr, Column.class, map("nullable", new BooleanMemberValue(true, constPool)));
                }

                if (attr.getAnnotations().length > 0) {
                    ctField.getFieldInfo().addAttribute(attr);
                }

                ctClass.addField(ctField);
            }

        } catch (Exception e) {
            e.printStackTrace();
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
