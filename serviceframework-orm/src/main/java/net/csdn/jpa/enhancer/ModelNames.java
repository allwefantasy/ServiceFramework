package net.csdn.jpa.enhancer;

import javassist.CtClass;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.StringMemberValue;
import net.csdn.common.Strings;
import net.csdn.common.enhancer.EnhancerHelper;

import javax.persistence.Entity;
import javax.persistence.Table;

import static net.csdn.common.collections.WowCollections.map;

/**
 * JPA entity names and physical table names. An omitted {@code @Entity.name}
 * becomes the binary name so two classes can share a simple name. The physical
 * table stays the explicit {@code @Table} name, otherwise the underscored simple name.
 * A binary name is never used as a table name.
 */
public final class ModelNames {

    private ModelNames() {
    }

    public static String ensureEntityName(CtClass type) throws Exception {
        defrost(type);
        String explicit = readEntityName(type);
        defrost(type);
        if (explicit != null && explicit.length() > 0) {
            return explicit;
        }
        String binaryName = type.getName();
        if (!type.hasAnnotation(Entity.class)) {
            EnhancerHelper.createAnnotation(type, Entity.class, map(
                    "name", new StringMemberValue(binaryName, type.getClassFile().getConstPool())
            ));
            return binaryName;
        }
        AnnotationsAttribute attribute = EnhancerHelper.getAnnotations(type);
        Annotation annotation = attribute.getAnnotation(Entity.class.getName());
        if (annotation == null) {
            EnhancerHelper.createAnnotation(type, Entity.class, map(
                    "name", new StringMemberValue(binaryName, type.getClassFile().getConstPool())
            ));
            return binaryName;
        }
        annotation.addMemberValue("name", new StringMemberValue(binaryName, attribute.getConstPool()));
        attribute.addAnnotation(annotation);
        return binaryName;
    }

    public static String readEntityName(CtClass type) throws Exception {
        if (type == null || !type.hasAnnotation(Entity.class)) {
            return null;
        }
        Entity entity = (Entity) type.getAnnotation(Entity.class);
        if (entity == null || entity.name() == null || entity.name().length() == 0) {
            return null;
        }
        return entity.name();
    }

    public static String physicalTableName(CtClass type) throws Exception {
        defrost(type);
        boolean present = type.hasAnnotation(Table.class);
        defrost(type);
        if (present) {
            Table table = (Table) type.getAnnotation(Table.class);
            defrost(type);
            if (table != null && table.name() != null && table.name().length() > 0) {
                return table.name();
            }
        }
        return Strings.toUnderscoreCase(type.getSimpleName());
    }

    public static void ensureTable(CtClass type, String tableName) throws Exception {
        defrost(type);
        boolean present = type.hasAnnotation(Table.class);
        defrost(type);
        if (present) {
            return;
        }
        EnhancerHelper.createAnnotation(type, Table.class, map(
                "name", new StringMemberValue(tableName, type.getClassFile().getConstPool())
        ));
    }

    public static String alias(String entityName) {
        if (entityName == null || entityName.length() == 0) {
            return "e";
        }
        int dot = entityName.lastIndexOf('.');
        String simple = dot < 0 ? entityName : entityName.substring(dot + 1);
        StringBuilder alias = new StringBuilder();
        for (int i = 0; i < simple.length(); i++) {
            char ch = simple.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch == '_' || (alias.length() > 0 && ch >= '0' && ch <= '9')) {
                alias.append(ch);
            }
        }
        if (alias.length() == 0) {
            return "e";
        }
        return alias.toString().toLowerCase();
    }

    public static void defrost(CtClass type) {
        if (type != null && type.isFrozen()) {
            type.defrost();
        }
    }
}
