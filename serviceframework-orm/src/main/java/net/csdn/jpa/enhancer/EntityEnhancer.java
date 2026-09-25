package net.csdn.jpa.enhancer;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtField;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.BooleanMemberValue;
import javassist.bytecode.annotation.EnumMemberValue;
import javassist.bytecode.annotation.StringMemberValue;
import net.csdn.annotation.validate.Validate;
import net.csdn.common.Strings;
import net.csdn.common.enhancer.DynamicBytecode;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.enhancer.EnhancerHelper;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.BitEnhancer;
import net.csdn.jpa.JPA;
import net.csdn.jpa.type.DBInfo;
import net.csdn.jpa.type.DBType;
import org.hibernate.annotations.DynamicInsert;

import javax.persistence.Column;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.DiscriminatorType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.MappedSuperclass;
import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.map;
import static net.csdn.common.enhancer.EnhancerHelper.createAnnotation;

/**
 * User: WilliamZhu
 * Date: 12-8-20
 * Time: 下午9:47
 */
public class EntityEnhancer implements BitEnhancer {
    private Settings settings;
    private DBInfo dbInfo = JPA.dbInfo();

    public EntityEnhancer(Settings settings) {
        this.settings = settings;
    }

    @Override
    public void enhance(List<ModelClass> roots) throws Exception {
        for (int i = 0; i < roots.size(); i++) {
            ModelClass modelClass = roots.get(i);
            if (modelClass.isLeafNode()) {
                processLeafEntity(modelClass, null);
                autoInjectProperty(modelClass);
                autoInjectGetSet(modelClass);
                continue;
            }
            processInheritanceEntity(modelClass);
        }
    }

    private void processInheritanceEntity(ModelClass root) throws Exception {
        CtClass ct = root.originClass;
        ModelNames.defrost(ct);
        boolean abstractRoot = Modifier.isAbstract(ct.getModifiers());
        boolean hasInheritance = ct.hasAnnotation(Inheritance.class);
        boolean hasMapped = ct.hasAnnotation(MappedSuperclass.class);
        boolean hasEntity = ct.hasAnnotation(Entity.class);
        if (!hasInheritance && !hasMapped && !hasEntity && abstractRoot) {
            createAnnotation(ct, MappedSuperclass.class, map());
            hasMapped = true;
        }
        List<ModelClass> nodes = root.hierarchy();
        if (hasMapped && !hasEntity && !hasInheritance) {
            for (int i = 0; i < nodes.size(); i++) {
                ModelClass node = nodes.get(i);
                if (node != root && Modifier.isAbstract(node.originClass.getModifiers())
                        && !node.originClass.hasAnnotation(Entity.class)
                        && !node.originClass.hasAnnotation(MappedSuperclass.class)) {
                    ModelNames.defrost(node.originClass);
                    createAnnotation(node.originClass, MappedSuperclass.class, map());
                }
            }
            List<ModelClass> leaves = root.findLeafNodes();
            for (int i = 0; i < leaves.size(); i++) {
                processLeafEntity(leaves.get(i), null);
            }
            for (int i = 0; i < leaves.size(); i++) {
                autoInjectProperty(leaves.get(i));
            }
            for (int i = 0; i < nodes.size(); i++) {
                ModelClass node = nodes.get(i);
                if (!node.isLeafNode()) {
                    autoInhanceProperty(node);
                }
                autoInjectGetSet(node);
            }
            return;
        }

        InheritanceType strategy = inheritanceStrategy(ct);
        String rootTable = null;
        if (strategy == InheritanceType.SINGLE_TABLE) {
            rootTable = ModelNames.physicalTableName(ct);
        }
        for (int i = 0; i < nodes.size(); i++) {
            ModelClass node = nodes.get(i);
            ModelNames.defrost(node.originClass);
            if (strategy == InheritanceType.SINGLE_TABLE && node != root) {
                ModelNames.ensureEntityName(node.originClass);
                node.physicalTable(rootTable);
                bindTable(node, rootTable);
            } else if (node.isLeafNode() || node == root || strategy == InheritanceType.JOINED || strategy == InheritanceType.TABLE_PER_CLASS) {
                processLeafEntity(node, strategy == InheritanceType.SINGLE_TABLE ? rootTable : null);
            } else {
                ModelNames.ensureEntityName(node.originClass);
            }
            autoInhanceProperty(node);
            autoInjectGetSet(node);
        }
        if (strategy == InheritanceType.SINGLE_TABLE) {
            List<ModelClass> leaves = root.findLeafNodes();
            for (int i = 0; i < leaves.size(); i++) {
                ModelClass leaf = leaves.get(i);
                leaf.physicalTable(rootTable);
                bindTable(leaf, rootTable);
                autoInjectProperty(leaf);
            }
        } else {
            for (int i = 0; i < nodes.size(); i++) {
                autoInjectProperty(nodes.get(i));
            }
        }
    }

    private static InheritanceType inheritanceStrategy(CtClass type) throws Exception {
        if (!type.hasAnnotation(Inheritance.class)) {
            return null;
        }
        Inheritance inheritance = (Inheritance) type.getAnnotation(Inheritance.class);
        if (inheritance == null) {
            return null;
        }
        return inheritance.strategy();
    }

    private void processLeafEntity(ModelClass modelClass, String forcedTable) throws Exception {
        CtClass ct = modelClass.originClass;
        ModelNames.defrost(ct);
        String entityName = ModelNames.ensureEntityName(ct);
        String tableName = forcedTable == null ? ModelNames.physicalTableName(ct) : forcedTable;
        if (tableName.indexOf('.') >= 0 && tableName.equals(ct.getName())) {
            throw mappingFailure(ct.getName(), "binary name cannot be used as a physical table name");
        }
        ModelNames.ensureTable(ct, tableName);
        ConstPool constPool = ct.getClassFile().getConstPool();
        EnhancerHelper.createAnnotation(ct, org.hibernate.annotations.Entity.class, map(
                "dynamicInsert", new BooleanMemberValue(true, constPool)
        ));
        EnhancerHelper.createAnnotation(ct, DynamicInsert.class, map());
        modelClass.physicalTable(tableName);
        bindTable(modelClass, tableName);
        if (entityName != null) {
            dbInfo.bind(entityName, tableName);
        }
    }

    private void bindTable(ModelClass modelClass, String tableName) {
        CtClass ct = modelClass.originClass;
        dbInfo.bind(tableName, tableName);
        dbInfo.bind(ct.getName(), tableName);
        String simpleName = ct.getSimpleName();
        dbInfo.bind(simpleName, tableName);
    }

    private void autoInhanceProperty(ModelClass modelClass) {
        try {
            List<String> skipFields = modelClass.notMappings();
            CtClass type = modelClass.originClass;
            ModelNames.defrost(type);
            ConstPool constPool = type.getClassFile().getConstPool();
            CtField[] fields = type.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                CtField ctField = fields[i];
                if (Modifier.isStatic(ctField.getModifiers())) {
                    continue;
                }
                if ((!skipFields.contains(ctField.getName()) && !skipFields.contains(Strings.toUnderscoreCase(ctField.getName())))
                        && ctField.getAnnotations().length == 0) {
                    if (ctField.getName().equals("discriminator")) {
                        continue;
                    }
                    annotatePersistedField(ctField, null, constPool);
                }
            }
        } catch (Exception e) {
            throw mappingFailure(modelClass.originClass.getName(), "property annotations were not added", e);
        }
    }

    private void autoInjectProperty(ModelClass modelClass) {
        CtClass ctClass = modelClass.originClass;
        ModelNames.defrost(ctClass);
        List<String> skipFields = modelClass.notMappings();
        try {
            DBType dbType = JPA.dbType();
            Map<String, String> columns = columnsFor(modelClass);
            if (columns == null || columns.isEmpty()) {
                return;
            }
            for (Map.Entry<String, String> column : columns.entrySet()) {
                String columnName = column.getKey();
                final String fieldName = Strings.toCamelCase(columnName, false);
                String fieldType = column.getValue();
                if (skipFields.contains(columnName) || skipFields.contains(fieldName)) {
                    continue;
                }
                if ("discriminator".equals(fieldName) || "dtype".equalsIgnoreCase(columnName)) {
                    continue;
                }
                CtField existing = ModelClass.findDeclaredField(ctClass, fieldName);
                if (existing != null) {
                    ConstPool declaringPool = existing.getDeclaringClass().getClassFile().getConstPool();
                    annotatePersistedField(existing, fieldType, declaringPool);
                    continue;
                }
                CtField ctField = CtField.make(
                        " private " + dbType.typeToJava(fieldType).v2() + " " + fieldName + " ;",
                        ctClass);
                annotatePersistedField(ctField, fieldType, ctClass.getClassFile().getConstPool());
                ctClass.addField(ctField);
            }
        } catch (EnhancementFailure failure) {
            throw failure;
        } catch (Exception e) {
            throw mappingFailure(ctClass.getName(), "columns were not mapped onto fields", e);
        }
        ModelNames.defrost(ctClass);
    }

    private Map<String, String> columnsFor(ModelClass modelClass) {
        if (modelClass.physicalTable() != null) {
            Map<String, String> columns = dbInfo.columns(modelClass.physicalTable());
            if (columns != null) {
                return columns;
            }
        }
        Map<String, String> byBinaryName = dbInfo.columns(modelClass.originClass.getName());
        if (byBinaryName != null) {
            return byBinaryName;
        }
        return dbInfo.columns(modelClass.originClass.getSimpleName());
    }

    private void annotatePersistedField(CtField ctField, String fieldType, ConstPool constPool) {
        DBType dbType = JPA.dbType();
        if (fieldType != null) {
            net.csdn.common.collect.Tuple<Class, Map> tuple = dbType.dateType(fieldType, constPool);
            if (tuple != null) {
                EnhancerHelper.createAnnotation(ctField, tuple.v1(), tuple.v2());
            }
        }
        String fieldName = ctField.getName();
        if (fieldName.equals("id")) {
            EnumMemberValue emv = new EnumMemberValue(constPool);
            emv.setType(GenerationType.class.getName());
            emv.setValue(GenerationType.IDENTITY.name());
            EnhancerHelper.createAnnotation(ctField, Id.class, map());
            EnhancerHelper.createAnnotation(ctField, GeneratedValue.class, map("strategy", emv));
        } else if (!ctField.hasAnnotation(Column.class) && !ctField.hasAnnotation(Id.class)) {
            EnhancerHelper.createAnnotation(ctField, Column.class, map(
                    "name", new StringMemberValue(Strings.toUnderscoreCase(fieldName), constPool),
                    "nullable", new BooleanMemberValue(true, constPool)
            ));
        }
        if (ctField.hasAnnotation(DiscriminatorColumn.class)) {
            return;
        }
    }

    private void autoInjectGetSet(ModelClass modelClass) throws Exception {
        CtClass ctClass = modelClass.originClass;
        ModelNames.defrost(ctClass);
        DynamicBytecode.addBeanAccessors(ctClass, new DynamicBytecode.CtFieldFilter() {
            @Override
            public boolean accept(CtField field) throws Exception {
                return DynamicBytecode.isInstanceDataField(field) && !field.hasAnnotation(Validate.class);
            }
        });
    }

    /**
     * Copies instance fields into the target constant pool. Static {@code parent$_}
     * fields are not copied here; query enhancement copies those and retargets
     * their lazy initialization per model. {@code <clinit>} is not copied.
     */
    static void copyFieldsToSubclass(CtClass source, CtClass target) throws CannotCompileException, NotFoundException {
        ModelNames.defrost(target);
        CtField[] fields = source.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            CtField field = fields[i];
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                target.getDeclaredField(field.getName());
                continue;
            } catch (NotFoundException ignored) {
                CtField copied = new CtField(field, target);
                target.addField(copied);
            }
        }
    }

    private static EnhancementFailure mappingFailure(String className, String detail, Exception cause) {
        return new EnhancementFailure(
                EnhancementFailure.Category.ENHANCEMENT,
                className,
                "entity-mapping",
                "enhance",
                detail,
                cause);
    }

    private static EnhancementFailure mappingFailure(String className, String detail) {
        return mappingFailure(className, detail, null);
    }
}
