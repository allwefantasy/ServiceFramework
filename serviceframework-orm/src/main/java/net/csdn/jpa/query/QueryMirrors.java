package net.csdn.jpa.query;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads {@link QueryMethod} mirrors without calling {@code Class.forName} or
 * {@code Element.getAnnotation(Class)}.
 */
final class QueryMirrors {

    private static final String CONTAINER = "net.csdn.jpa.query.GenerateQueries";
    private static final String METHOD = "net.csdn.jpa.query.QueryMethod";

    private QueryMirrors() {
    }

    static List<QueryMethodSpec> read(String modelName, Element element, Elements elements) {
        List<QueryMethodSpec> contained = new ArrayList<QueryMethodSpec>();
        List<QueryMethodSpec> loose = new ArrayList<QueryMethodSpec>();
        boolean sawContainer = false;
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            String name = annotationName(mirror);
            if (CONTAINER.equals(name)) {
                sawContainer = true;
                contained.addAll(readContainer(modelName, mirror, elements));
            } else if (METHOD.equals(name)) {
                loose.add(readMethod(modelName, mirror, elements));
            }
        }
        return sawContainer ? contained : loose;
    }

    private static List<QueryMethodSpec> readContainer(String modelName, AnnotationMirror mirror, Elements elements) {
        AnnotationValue value = member(mirror, elements, "value");
        if (value == null || !(value.getValue() instanceof List)) {
            throw new QueryDeclarationException(modelName, "-", "-", "@GenerateQueries.value is missing");
        }
        List<?> raw = (List<?>) value.getValue();
        List<QueryMethodSpec> specs = new ArrayList<QueryMethodSpec>();
        for (int i = 0; i < raw.size(); i++) {
            Object item = raw.get(i);
            if (!(item instanceof AnnotationValue) || !(((AnnotationValue) item).getValue() instanceof AnnotationMirror)) {
                throw new QueryDeclarationException(modelName, "-", "-", "@GenerateQueries contains a non-annotation value");
            }
            specs.add(readMethod(modelName, (AnnotationMirror) ((AnnotationValue) item).getValue(), elements));
        }
        return specs;
    }

    private static QueryMethodSpec readMethod(String modelName, AnnotationMirror mirror, Elements elements) {
        String name = stringMember(modelName, mirror, elements, "name");
        return new QueryMethodSpec(name,
                stringArray(modelName, mirror, elements, "fields"),
                stringArray(modelName, mirror, elements, "orderBy"));
    }

    private static String stringMember(String modelName, AnnotationMirror mirror, Elements elements, String memberName) {
        AnnotationValue value = member(mirror, elements, memberName);
        if (value == null) {
            throw new QueryDeclarationException(modelName, "-", "-", "@QueryMethod." + memberName + " is missing");
        }
        String text = asString(value.getValue());
        if (text == null) {
            throw new QueryDeclarationException(modelName, "-", memberName,
                    "@QueryMethod member must be a string literal");
        }
        return text;
    }

    private static List<String> stringArray(String modelName, AnnotationMirror mirror, Elements elements, String memberName) {
        AnnotationValue value = member(mirror, elements, memberName);
        if (value == null) {
            return new ArrayList<String>();
        }
        if (!(value.getValue() instanceof List)) {
            throw new QueryDeclarationException(modelName, "-", memberName, "@QueryMethod member must be a string array");
        }
        List<?> raw = (List<?>) value.getValue();
        List<String> values = new ArrayList<String>();
        for (int i = 0; i < raw.size(); i++) {
            Object item = raw.get(i);
            Object unwrapped = item instanceof AnnotationValue ? ((AnnotationValue) item).getValue() : item;
            String text = asString(unwrapped);
            if (text == null) {
                throw new QueryDeclarationException(modelName, "-", memberName,
                        "@QueryMethod member must be a string literal");
            }
            values.add(text);
        }
        return values;
    }

    private static AnnotationValue member(AnnotationMirror mirror, Elements elements, String name) {
        Map<? extends ExecutableElement, ? extends AnnotationValue> values = elements.getElementValuesWithDefaults(mirror);
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : values.entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String asString(Object raw) {
        if (raw instanceof String) {
            return (String) raw;
        }
        if (raw instanceof VariableElement) {
            Object constant = ((VariableElement) raw).getConstantValue();
            if (constant instanceof String) {
                return (String) constant;
            }
        }
        return null;
    }

    private static String annotationName(AnnotationMirror mirror) {
        Element element = mirror.getAnnotationType().asElement();
        if (element instanceof TypeElement) {
            return ((TypeElement) element).getQualifiedName().toString();
        }
        return "";
    }
}
