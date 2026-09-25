package net.csdn.jpa.query;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.util.List;

/**
 * Model view backed by {@code javax.lang.model}. It does not load the model class.
 */
final class LangQueryModelView implements QueryModelView {

    private static final String MODEL = "net.csdn.jpa.model.Model";
    private static final String JPA_BASE = "net.csdn.jpa.model.JPABase";

    private final TypeElement type;

    LangQueryModelView(TypeElement type) {
        this.type = type;
    }

    @Override
    public String getModelName() {
        return type.getQualifiedName().toString();
    }

    @Override
    public QueryFieldView findField(String name) {
        TypeElement current = type;
        while (current != null) {
            String qualified = current.getQualifiedName().toString();
            if ("java.lang.Object".equals(qualified) || MODEL.equals(qualified) || JPA_BASE.equals(qualified)) {
                return null;
            }
            List<VariableElement> fields = ElementFilter.fieldsIn(current.getEnclosedElements());
            for (int i = 0; i < fields.size(); i++) {
                VariableElement field = fields.get(i);
                if (field.getSimpleName().contentEquals(name)) {
                    return new LangField(field);
                }
            }
            TypeMirror superclass = current.getSuperclass();
            if (superclass.getKind() == TypeKind.NONE) {
                return null;
            }
            if (superclass.getKind() != TypeKind.DECLARED) {
                throw new QueryDeclarationException(getModelName(), "-", name,
                        "cannot resolve superclass " + superclass + " from language model metadata");
            }
            Element superElement = ((DeclaredType) superclass).asElement();
            if (!(superElement instanceof TypeElement)) {
                throw new QueryDeclarationException(getModelName(), "-", name,
                        "superclass is not a type element");
            }
            current = (TypeElement) superElement;
        }
        return null;
    }

    private static final class LangField implements QueryFieldView {
        private final VariableElement field;
        private final String javaType;
        private final boolean primitive;
        private final boolean enumeration;
        private final boolean eligible;
        private final String reason;
        private final boolean scalar;

        private LangField(VariableElement field) {
            this.field = field;
            TypeMirror mirror = field.asType();
            this.primitive = mirror.getKind().isPrimitive();
            this.enumeration = isEnumType(mirror);
            this.javaType = typeName(mirror);
            String eligibility = eligibility(field);
            this.eligible = eligibility == null;
            this.reason = eligibility;
            this.scalar = enumeration || QuerySchemas.isSupportedScalarName(javaType);
        }

        @Override
        public String getName() {
            return field.getSimpleName().toString();
        }

        @Override
        public String getJavaType() {
            return javaType;
        }

        @Override
        public boolean isPrimitive() {
            return primitive;
        }

        @Override
        public boolean isEnum() {
            return enumeration;
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
            return scalar;
        }
    }

    private static boolean isEnumType(TypeMirror mirror) {
        if (mirror.getKind() != TypeKind.DECLARED) {
            return false;
        }
        Element element = ((DeclaredType) mirror).asElement();
        return element.getKind() == ElementKind.ENUM;
    }

    private static String typeName(TypeMirror mirror) {
        if (mirror.getKind().isPrimitive()) {
            return primitiveName(mirror.getKind());
        }
        if (mirror.getKind() == TypeKind.DECLARED) {
            Element element = ((DeclaredType) mirror).asElement();
            if (element instanceof TypeElement) {
                return ((TypeElement) element).getQualifiedName().toString();
            }
        }
        if (mirror.getKind() == TypeKind.ARRAY) {
            return arrayName((ArrayType) mirror);
        }
        return mirror.toString();
    }

    private static String arrayName(ArrayType array) {
        int dimensions = 1;
        TypeMirror component = array.getComponentType();
        while (component.getKind() == TypeKind.ARRAY) {
            dimensions++;
            component = ((ArrayType) component).getComponentType();
        }
        StringBuilder name = new StringBuilder(typeName(component));
        for (int i = 0; i < dimensions; i++) {
            name.append("[]");
        }
        return name.toString();
    }

    private static String eligibility(VariableElement field) {
        String name = field.getSimpleName().toString();
        if (field.getKind() != ElementKind.FIELD || name.indexOf('$') >= 0) {
            return "excluded by the dynamic finder field filter (synthetic)";
        }
        if (field.getModifiers().contains(Modifier.STATIC)) {
            return "excluded by the dynamic finder field filter (static)";
        }
        if (field.getModifiers().contains(Modifier.FINAL)) {
            return "excluded by the dynamic finder field filter (final)";
        }
        if (field.getModifiers().contains(Modifier.TRANSIENT) || hasAnnotation(field, "javax.persistence.Transient")) {
            return "excluded by the dynamic finder field filter (transient)";
        }
        if (isAssociation(field)) {
            return "association field is outside the limited equality grammar";
        }
        return null;
    }

    private static String primitiveName(TypeKind kind) {
        switch (kind) {
            case BOOLEAN:
                return "boolean";
            case BYTE:
                return "byte";
            case SHORT:
                return "short";
            case INT:
                return "int";
            case LONG:
                return "long";
            case CHAR:
                return "char";
            case FLOAT:
                return "float";
            case DOUBLE:
                return "double";
            default:
                return null;
        }
    }

    private static boolean isAssociation(VariableElement field) {
        return hasAnnotation(field, "javax.persistence.ManyToOne")
                || hasAnnotation(field, "javax.persistence.OneToOne")
                || hasAnnotation(field, "javax.persistence.OneToMany")
                || hasAnnotation(field, "javax.persistence.ManyToMany")
                || hasAnnotation(field, "javax.persistence.Embedded")
                || hasAnnotation(field, "javax.persistence.EmbeddedId")
                || hasAnnotation(field, "javax.persistence.ElementCollection");
    }

    private static boolean hasAnnotation(Element element, String qualifiedName) {
        for (javax.lang.model.element.AnnotationMirror mirror : element.getAnnotationMirrors()) {
            Element annotation = mirror.getAnnotationType().asElement();
            if (annotation instanceof TypeElement
                    && qualifiedName.contentEquals(((TypeElement) annotation).getQualifiedName())) {
                return true;
            }
        }
        return false;
    }
}
