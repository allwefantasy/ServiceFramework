package net.csdn.jpa.query;

import javax.persistence.ElementCollection;
import javax.persistence.Embedded;
import javax.persistence.EmbeddedId;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Transient;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Runtime view of a model class that has already been loaded.
 * Framework base types are skipped by name so this class does not use them as query fields.
 */
final class ReflectiveQueryModelView implements QueryModelView {

    private static final String MODEL = "net.csdn.jpa.model.Model";
    private static final String JPA_BASE = "net.csdn.jpa.model.JPABase";

    private final Class<?> model;

    ReflectiveQueryModelView(Class<?> model) {
        this.model = model;
    }

    @Override
    public String getModelName() {
        return model.getName();
    }

    @Override
    public QueryFieldView findField(String name) {
        Field field = findJavaField(name);
        if (field == null) {
            return null;
        }
        return new ReflectiveField(field);
    }

    Class<?> runtimeType(String name) {
        Field field = findJavaField(name);
        return field == null ? null : field.getType();
    }

    private Field findJavaField(String name) {
        Class<?> current = model;
        while (current != null) {
            String qualified = current.getName();
            if ("java.lang.Object".equals(qualified) || MODEL.equals(qualified) || JPA_BASE.equals(qualified)) {
                return null;
            }
            Field[] fields = current.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                if (fields[i].getName().equals(name)) {
                    return fields[i];
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static final class ReflectiveField implements QueryFieldView {
        private final Field field;
        private final String javaType;
        private final boolean eligible;
        private final String reason;

        private ReflectiveField(Field field) {
            this.field = field;
            this.javaType = typeName(field.getType());
            this.reason = eligibility(field);
            this.eligible = reason == null;
        }

        @Override
        public String getName() {
            return field.getName();
        }

        @Override
        public String getJavaType() {
            return javaType;
        }

        @Override
        public boolean isPrimitive() {
            return field.getType().isPrimitive();
        }

        @Override
        public boolean isEnum() {
            return field.getType().isEnum();
        }

        @Override
        public boolean isEligible() {
            return eligible;
        }

        @Override
        public String getIneligibleReason() {
            return reason == null ? "field is not eligible" : reason;
        }

        @Override
        public boolean isSupportedScalar() {
            return isEnum() || QuerySchemas.isSupportedScalarName(javaType);
        }
    }

    private static String typeName(Class<?> type) {
        String canonical = type.getCanonicalName();
        if (canonical != null) {
            return canonical;
        }
        return type.getName().replace('$', '.');
    }

    private static String eligibility(Field field) {
        if (field.isSynthetic() || field.getName().indexOf('$') >= 0) {
            return "excluded by the dynamic finder field filter (synthetic)";
        }
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers)) {
            return "excluded by the dynamic finder field filter (static)";
        }
        if (Modifier.isFinal(modifiers)) {
            return "excluded by the dynamic finder field filter (final)";
        }
        if (Modifier.isTransient(modifiers) || field.getAnnotation(Transient.class) != null) {
            return "excluded by the dynamic finder field filter (transient)";
        }
        if (field.getAnnotation(ManyToOne.class) != null
                || field.getAnnotation(OneToOne.class) != null
                || field.getAnnotation(OneToMany.class) != null
                || field.getAnnotation(ManyToMany.class) != null
                || field.getAnnotation(Embedded.class) != null
                || field.getAnnotation(EmbeddedId.class) != null
                || field.getAnnotation(ElementCollection.class) != null) {
            return "association field is outside the limited equality grammar";
        }
        return null;
    }
}
