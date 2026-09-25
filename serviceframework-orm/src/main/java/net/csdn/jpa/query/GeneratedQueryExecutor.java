package net.csdn.jpa.query;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.List;

/**
 * Runs a companion query declared by {@link QueryMethod}.
 * Generated {@code ModelQueries} methods call {@link #execute(Class, String, Object[], int, int)},
 * which reads the EntityManager from {@code JPA.getJPAConfig().getJPAContext().em()}
 * after the application context exists, or the explicit overload
 * {@link #execute(EntityManager, Class, String, Object[], int, int)} that takes
 * the caller's own EntityManager. There is no shared EntityManager state here:
 * every call resolves its EntityManager from an argument or from the JPA context.
 *
 * <p>User values are bound with {@code setParameter}. They are not concatenated into JPQL.
 * A null reference value is {@code IS NULL} and is not bound. The entity name comes from
 * {@code EntityManager.getMetamodel().entity(model).getName()}, not from the simple class name.
 */
public final class GeneratedQueryExecutor {

    private GeneratedQueryExecutor() {
    }

    public static <T> List<T> execute(Class<T> model, String methodName, Object[] values, int offset, int limit) {
        return execute(JpaEntityManagers.current(), model, methodName, values, offset, limit);
    }

    public static <T> List<T> execute(EntityManager entityManager, Class<T> model, String methodName, Object[] values, int offset, int limit) {
        if (model == null) {
            throw new QueryDeclarationException("-", methodName, "-", "model class is missing");
        }
        if (methodName == null || methodName.length() == 0) {
            throw new QueryDeclarationException(model.getName(), "-", "-", "query method name is empty");
        }
        if (offset < 0) {
            throw new IllegalArgumentException(QueryDeclarationException.diagnostic(model.getName(), methodName, "-",
                    "offset must be >= 0"));
        }
        if (limit < 1 || limit > QueryLimits.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(QueryDeclarationException.diagnostic(model.getName(), methodName, "-",
                    "limit must be between 1 and " + QueryLimits.MAX_PAGE_SIZE));
        }
        QueryMetadata metadata = RuntimeQueryMetadata.read(model);
        QueryDescriptor descriptor = metadata.require(methodName);
        if (values == null || values.length != descriptor.getPredicates().size()) {
            throw new IllegalArgumentException(QueryDeclarationException.diagnostic(model.getName(), methodName, "-",
                    "argument count does not match declared equality fields"));
        }
        boolean[] nullValues = new boolean[values.length];
        ReflectiveQueryModelView view = new ReflectiveQueryModelView(model);
        for (int i = 0; i < values.length; i++) {
            QueryPredicate predicate = descriptor.getPredicates().get(i);
            nullValues[i] = values[i] == null;
            checkValue(model.getName(), methodName, predicate, view.runtimeType(predicate.getFieldName()), values[i]);
        }
        if (entityManager == null) {
            throw new IllegalStateException("EntityManager is not available. Call after JPA.getJPAConfig().getJPAContext() exists.");
        }
        String entityName = entityManager.getMetamodel().entity(model).getName();
        String jpql = QueryJpql.render(entityName, descriptor, nullValues);
        TypedQuery<T> query = entityManager.createQuery(jpql, model);
        for (int i = 0; i < descriptor.getPredicates().size(); i++) {
            if (nullValues[i]) {
                continue;
            }
            query.setParameter(descriptor.getPredicates().get(i).getParameterName(), values[i]);
        }
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    private static void checkValue(String modelName, String methodName, QueryPredicate predicate, Class<?> expected, Object value) {
        if (expected == null) {
            throw new QueryDeclarationException(modelName, methodName, predicate.getFieldName(), "field disappeared at runtime");
        }
        if (value == null) {
            if (expected.isPrimitive() || predicate.isPrimitive()) {
                throw new IllegalArgumentException(QueryDeclarationException.diagnostic(modelName, methodName, predicate.getFieldName(),
                        "primitive field does not accept null"));
            }
            return;
        }
        Class<?> accepted = expected.isPrimitive() ? wrapper(expected) : expected;
        if (!accepted.isInstance(value)) {
            throw new IllegalArgumentException(QueryDeclarationException.diagnostic(modelName, methodName, predicate.getFieldName(),
                    "value type " + value.getClass().getName() + " is not " + predicate.getJavaType()));
        }
    }

    private static Class<?> wrapper(Class<?> primitive) {
        if (primitive == Integer.TYPE) {
            return Integer.class;
        }
        if (primitive == Long.TYPE) {
            return Long.class;
        }
        if (primitive == Boolean.TYPE) {
            return Boolean.class;
        }
        if (primitive == Double.TYPE) {
            return Double.class;
        }
        if (primitive == Float.TYPE) {
            return Float.class;
        }
        if (primitive == Short.TYPE) {
            return Short.class;
        }
        if (primitive == Byte.TYPE) {
            return Byte.class;
        }
        if (primitive == Character.TYPE) {
            return Character.class;
        }
        return primitive;
    }
}
