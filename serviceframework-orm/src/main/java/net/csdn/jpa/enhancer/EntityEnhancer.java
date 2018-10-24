package net.csdn.jpa.enhancer;

import javassist.*;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.BooleanMemberValue;
import javassist.bytecode.annotation.EnumMemberValue;
import javassist.bytecode.annotation.StringMemberValue;
import net.csdn.annotation.validate.Validate;
import net.csdn.common.Strings;
import net.csdn.common.enhancer.EnhancerHelper;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.BitEnhancer;
import net.csdn.jpa.JPA;
import net.csdn.jpa.type.DBInfo;
import net.csdn.jpa.type.DBType;
import org.apache.commons.lang.StringUtils;
import org.hibernate.annotations.DynamicInsert;

import javax.persistence.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.csdn.common.collections.WowCollections.list;
import static net.csdn.common.collections.WowCollections.map;
import static net.csdn.common.enhancer.EnhancerHelper.createAnnotation;

/**
 * User: WilliamZhu
 * Date: 12-8-20
 * Time: 下午9:47
 */
public class EntityEnhancer implements BitEnhancer {
    private Settings settings;
    private CSLogger logger = Loggers.getLogger(getClass());
    private DBInfo dbInfo = JPA.dbInfo();

    public EntityEnhancer(Settings settings) {
        this.settings = settings;
    }

    @Override
    public void enhance(List<ModelClass> roots) throws Exception {

        //SINGLE_TABLE, TABLE_PER_CLASS, JOINED
        for (ModelClass modelClass : roots) {
            if (modelClass.isLeafNode()) {
                //no inheritance hierarchy
                processLeafEntity(modelClass);
                autoInjectProperty(modelClass);
                autoInjectGetSet(modelClass);
                continue;
            }
            processInheritanceEntity(modelClass);
        }
    }

    private void processEntityDiscriminatorColumn(ModelClass modelClass) {
        CtClass ctClass = modelClass.originClass;
        Map<String, String> columns = dbInfo.tableColumns.get(ctClass.getSimpleName());

        if (columns != null) {
            if (!ctClass.hasAnnotation(DiscriminatorColumn.class)) {
                for (String columnName : columns.keySet()) {
                    if (columnName.equals("discriminator")) {
                        ConstPool constPool = ctClass.getClassFile().getConstPool();

                        EnumMemberValue emb = new EnumMemberValue(constPool);
                        emb.setType("javax.persistence.DiscriminatorType");
                        emb.setValue("STRING");
                        createAnnotation(ctClass, DiscriminatorColumn.class, map(
                                "name", new StringMemberValue(columnName, constPool),
                                "discriminatorType", emb
                        ));

                        //@Inheritance(strategy=InheritanceType.SINGLE_TABLE)
                        EnumMemberValue strategy = new EnumMemberValue(constPool);
                        strategy.setType("javax.persistence.InheritanceType");
                        strategy.setValue("SINGLE_TABLE");
                        createAnnotation(ctClass, Inheritance.class, map(
                                "strategy", strategy
                        ));

                        //SINGLE_TABLE only have one table
                        for (ModelClass mc : modelClass.findLeafNodes()) {
                            dbInfo.tableColumns.put(mc.originClass.getName(), columns);
                        }

                        break;
                    }

                }
            }
        }
    }


    private void processLeafEntity(ModelClass modelClass) throws Exception {
        CtClass ct = modelClass.originClass;
        ConstPool constPool = ct.getClassFile().getConstPool();
        EnhancerHelper.createAnnotation(ct, Entity.class, map());
        Entity entity = (Entity) ct.getAnnotation(Entity.class);
        String entityName = StringUtils.isEmpty(entity.name()) ? Strings.toUnderscoreCase(ct.getSimpleName()) : entity.name();

        if (!ct.hasAnnotation(Table.class)) {
            EnhancerHelper.createAnnotation(ct, Table.class, map("name", new StringMemberValue(entityName, constPool)));
        }
        EnhancerHelper.createAnnotation(ct, org.hibernate.annotations.Entity.class, map("dynamicInsert", new BooleanMemberValue(true, constPool)));
        EnhancerHelper.createAnnotation(ct, DynamicInsert.class, map());
        dbInfo.tableColumns.put(ct.getSimpleName(), dbInfo.tableColumns.get(entityName));
    }

    /*
         JPA Inheritance Hierarchy is little complex.
         So we will copy all fields to leaf class and enhance leaf class
     */
    private void processInheritanceEntity(ModelClass modelClass) throws Exception {
        CtClass ct = modelClass.originClass;
        //default behavior , if a Class is abstract,adding Inheritance & MappedSuperclass annotation
        List<ModelClass> leafNodes = modelClass.findLeafNodes();
        if (!ct.hasAnnotation(Inheritance.class) && Modifier.isAbstract(ct.getModifiers())) {
            EnhancerHelper.createAnnotation(
                    ct,
                    MappedSuperclass.class,
                    map()
            );
            ConstPool constPool = ct.getClassFile().getConstPool();
            EnumMemberValue strategy = new EnumMemberValue(constPool);
            strategy.setType("javax.persistence.InheritanceType");
            strategy.setValue("JOINED");
            EnhancerHelper.createAnnotation(
                    ct,
                    Inheritance.class,
                    map("strategy", strategy)
            );
        }

        for (ModelClass mc : leafNodes) {
            processLeafEntity(mc);
            autoInjectGetSet(mc);
        }
        autoInjectGetSet(modelClass);
        MappedSuperclass mappedSuperclass = (MappedSuperclass) ct.getAnnotation(MappedSuperclass.class);
        if (mappedSuperclass != null) {
            //modelClass have no table to mapping
            autoInhanceProperty(modelClass);
            autoInjectGetSet(modelClass);
            AssociationEnhancer associationEnhancer = new AssociationEnhancer(settings);
            for (ModelClass mc : leafNodes) {
                autoInjectProperty(mc);
            }
            associationEnhancer.enhance(list(modelClass));
        } else {
            Inheritance inheritance = (Inheritance) ct.getAnnotation(Inheritance.class);
            if (inheritance.strategy().equals(InheritanceType.JOINED)) {

            } else if (inheritance.strategy().equals(InheritanceType.SINGLE_TABLE)) {
                //do nothing
            } else if (inheritance.strategy().equals(InheritanceType.TABLE_PER_CLASS)) {
                autoInjectProperty(modelClass);
                for (ModelClass mc : leafNodes) {
                    autoInjectProperty(mc);
                }
            }
        }

    }

    private void copyFields(final ModelClass modelClass) {
        ModelClass.iterateSuperClass(modelClass.originClass, new ModelClass.SuperClassIterator() {
            @Override
            public void iterate(CtClass ctClass) {
                try {
                    copyFieldsToSubclass(ctClass, modelClass.originClass);
                } catch (Exception e) {

                }
            }
        });

    }

    private void autoInhanceProperty(ModelClass modelClass) {
        try {
            List<String> skipFields = modelClass.notMappings();
            ConstPool constPool = modelClass.originClass.getClassFile().getConstPool();
            CtField[] fields = modelClass.originClass.getDeclaredFields();
            for (CtField ctField : fields) {
                if ((!skipFields.contains(ctField.getName()) && !skipFields.contains(Strings.toUnderscoreCase(ctField.getName())))
                        && ctField.getAnnotations().length == 0) {
                    if (ctField.getName().equals("discriminator")) continue;
                    if (ctField.getName().equals("id")) {
                        createAnnotation(ctField, Id.class, map());
                        EnumMemberValue emv = new EnumMemberValue(constPool);
                        emv.setType(GenerationType.class.getName());
                        emv.setValue(GenerationType.IDENTITY.name());
                        EnhancerHelper.createAnnotation(ctField, GeneratedValue.class, map("strategy", emv));
                    } else {
                        createAnnotation(ctField, Column.class, map("name", new StringMemberValue(Strings.toUnderscoreCase(ctField.getName()), constPool), "nullable", new BooleanMemberValue(true, constPool)));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void autoInjectProperty(ModelClass modelClass) {


        CtClass ctClass = modelClass.originClass;
        List<String> skipFields = modelClass.notMappings();
        String entitySimpleName = ctClass.getSimpleName();

        try {
            DBType dbType = JPA.dbType();
            DBInfo dbInfo = JPA.dbInfo();

            Map<String, String> columns = dbInfo.tableColumns.get(entitySimpleName);
            if (columns == null) return;

            ConstPool constPool = ctClass.getClassFile().getConstPool();

            for (String columnName : columns.keySet()) {
                final String fieldName = Strings.toCamelCase(columnName, false);
                String fieldType = columns.get(columnName);
                if (skipFields.contains(columnName) || skipFields.contains(fieldName)) continue;
                if (fieldName.equals("discriminator")) continue;

                //对定义过的属性略过
                final AtomicBoolean pass = new AtomicBoolean(true);

                try {
                    ctClass.getDeclaredField(fieldName);
                } catch (Exception e) {
                    ModelClass.iterateSuperClass(ctClass, new ModelClass.SuperClassIterator() {
                        @Override
                        public void iterate(CtClass ctClass) {
                            if (!pass.get()) return;
                            try {
                                ctClass.getDeclaredField(fieldName);
                            } catch (Exception e) {
                                pass.set(false);
                            }
                        }
                    });
                }

                if (pass.get()) {
                    CtField ctField = ctClass.getField(fieldName);
                    addColumnAnnotation(ctField, dbType, fieldType, constPool);
                    continue;
                }

                CtField ctField = CtField.make(" private " + dbType.typeToJava(fieldType).v2() + " " + fieldName + " ;", ctClass);
                addColumnAnnotation(ctField, dbType, fieldType, constPool);
                ctClass.addField(ctField);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        ctClass.defrost();
    }

    private void addColumnAnnotation(CtField ctField, DBType dbType, String fieldType, ConstPool constPool) {
        net.csdn.common.collect.Tuple<Class, Map> tuple = dbType.dateType(fieldType, constPool);
        if (tuple != null) {
            EnhancerHelper.createAnnotation(ctField, tuple.v1(), tuple.v2());
        }
        String fieldName = ctField.getName();
        if (fieldName.equals("id")) {
            EnumMemberValue emv = new EnumMemberValue(constPool);
            emv.setType(GenerationType.class.getName());
            emv.setValue(GenerationType.IDENTITY.name());

            EnhancerHelper.createAnnotation(ctField, Id.class, map());
            EnhancerHelper.createAnnotation(ctField, GeneratedValue.class, map("strategy", emv));
        } else {
            EnhancerHelper.createAnnotation(ctField, Column.class, map("name", new StringMemberValue(Strings.toUnderscoreCase(fieldName), constPool), "nullable", new BooleanMemberValue(true, constPool)));
        }
    }

    boolean isFinal(CtField ctField) {
        return java.lang.reflect.Modifier.isFinal(ctField.getModifiers());
    }

    boolean isStatic(CtField ctField) {
        return java.lang.reflect.Modifier.isStatic(ctField.getModifiers());
    }

    private void autoInjectGetSet(ModelClass modelClass) throws Exception {


        //hibernate 可能需要 setter/getter 方法，好吧 我们为它添加这些方法
        CtClass ctClass = modelClass.originClass;
        for (CtField ctField : ctClass.getDeclaredFields()) {
            if (isFinal(ctField) || isStatic(ctField) || ctField.hasAnnotation(Validate.class))
                continue;
            // Property name
            String propertyName = ctField.getName().substring(0, 1).toUpperCase() + ctField.getName().substring(1);
            String getter = "get" + propertyName;
            String setter = "set" + propertyName;

            try {
                CtMethod ctMethod = ctClass.getDeclaredMethod(getter);
                if (ctMethod.getParameterTypes().length > 0 || java.lang.reflect.Modifier.isStatic(ctMethod.getModifiers())) {
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
                if (ctMethod.getParameterTypes().length != 1 || !ctMethod.getParameterTypes()[0].equals(ctField.getType()) || java.lang.reflect.Modifier.isStatic(ctMethod.getModifiers())) {
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

    private static void copyFieldsToSubclass(CtClass document, CtClass targetClass) throws Exception {
        CtField[] ctFields = document.getDeclaredFields();
        for (CtField ctField : ctFields) {
            if (Modifier.isStatic(ctField.getModifiers())) continue;
            CtField ctField1 = new CtField(ctField.getType(), ctField.getName(), targetClass);
            ctField1.setModifiers(ctField.getModifiers());
            ctField1.getFieldInfo().getAttributes().addAll(ctField.getFieldInfo().getAttributes());
            targetClass.addField(ctField1);
        }

    }
}
