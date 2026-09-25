package net.csdn.jpa.query;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads companion-query declarations from a model class after application
 * enhancement has finished. Callers use the returned {@link QueryMetadata}
 * with the same validator as annotation processing.
 *
 * <p>This type does not look up {@code ServiceFrameworkPackageAnchor} and is
 * not a ClassDefiner helper. The anchor exists only so a later class-definition
 * step can name a neighbor class in the same package.
 */
public final class RuntimeQueryMetadata {

    private RuntimeQueryMetadata() {
    }

    public static QueryMetadata read(Class<?> model) {
        if (model == null) {
            throw new QueryDeclarationException("-", "-", "-", "model class is missing");
        }
        if (model.isArray() || model.isPrimitive() || model.isAnnotation()) {
            throw new QueryDeclarationException(model.getName(), "-", "-", "model must be a class");
        }
        if (QuerySources.ANCHOR_SIMPLE_NAME.equals(model.getSimpleName())) {
            throw new IllegalArgumentException("RuntimeQueryMetadata does not resolve ServiceFrameworkPackageAnchor; "
                    + "it is only a JDK 17 class-definition anchor and is not used by ClassDefiner");
        }
        return QuerySchemas.compile(new ReflectiveQueryModelView(model), specifications(model));
    }

    private static List<QueryMethodSpec> specifications(Class<?> model) {
        QueryMethod[] methods = model.getAnnotationsByType(QueryMethod.class);
        List<QueryMethodSpec> specs = new ArrayList<QueryMethodSpec>();
        for (int i = 0; i < methods.length; i++) {
            QueryMethod method = methods[i];
            specs.add(new QueryMethodSpec(method.name(), copy(method.fields()), copy(method.orderBy())));
        }
        return specs;
    }

    private static List<String> copy(String[] values) {
        List<String> copy = new ArrayList<String>();
        if (values == null) {
            return copy;
        }
        for (int i = 0; i < values.length; i++) {
            copy.add(values[i]);
        }
        return copy;
    }
}
