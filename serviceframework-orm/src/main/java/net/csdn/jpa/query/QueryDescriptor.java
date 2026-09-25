package net.csdn.jpa.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable description of one declared query method.
 * Processor output and {@link RuntimeQueryMetadata} both produce this type
 * from {@link QuerySchemas}; JPQL text is not part of the descriptor.
 */
public final class QueryDescriptor {

    private final String modelName;
    private final String methodName;
    private final List<QueryPredicate> predicates;
    private final List<QuerySort> sorts;

    public QueryDescriptor(String modelName, String methodName, List<QueryPredicate> predicates, List<QuerySort> sorts) {
        this.modelName = modelName;
        this.methodName = methodName;
        this.predicates = Collections.unmodifiableList(new ArrayList<QueryPredicate>(predicates));
        this.sorts = Collections.unmodifiableList(new ArrayList<QuerySort>(sorts));
    }

    public String getModelName() {
        return modelName;
    }

    public String getMethodName() {
        return methodName;
    }

    public List<QueryPredicate> getPredicates() {
        return predicates;
    }

    public List<QuerySort> getSorts() {
        return sorts;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof QueryDescriptor)) {
            return false;
        }
        QueryDescriptor that = (QueryDescriptor) other;
        return Objects.equals(modelName, that.modelName)
                && Objects.equals(methodName, that.methodName)
                && predicates.equals(that.predicates)
                && sorts.equals(that.sorts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelName, methodName, predicates, sorts);
    }

    @Override
    public String toString() {
        return modelName + "#" + methodName + " " + predicates + " order " + sorts;
    }
}
