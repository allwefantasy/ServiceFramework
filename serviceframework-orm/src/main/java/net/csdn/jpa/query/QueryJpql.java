package net.csdn.jpa.query;

/**
 * The only JPQL renderer for companion queries.
 * Field names and the entity name must already be validated identifiers.
 * Argument values are not accepted here and cannot be concatenated.
 */
final class QueryJpql {

    private QueryJpql() {
    }

    static String render(String entityName, QueryDescriptor descriptor, boolean[] nullValues) {
        if (!QueryIdentifiers.isEntityName(entityName)) {
            throw new QueryDeclarationException(descriptor == null ? "-" : descriptor.getModelName(),
                    descriptor == null ? "-" : descriptor.getMethodName(),
                    "-",
                    "entity name is not a dotted identifier and was not inserted into JPQL: " + entityName);
        }
        if (descriptor == null) {
            throw new QueryDeclarationException("-", "-", "-", "query descriptor is missing");
        }
        ListView predicates = new ListView(descriptor);
        if (nullValues == null || nullValues.length != predicates.size) {
            throw new QueryDeclarationException(descriptor.getModelName(), descriptor.getMethodName(), "-",
                    "null flags do not match equality fields");
        }
        StringBuilder jpql = new StringBuilder(128);
        jpql.append("SELECT e FROM ").append(entityName).append(" e WHERE ");
        for (int i = 0; i < predicates.size; i++) {
            QueryPredicate predicate = descriptor.getPredicates().get(i);
            if (predicate.getOperator() != QueryOperator.EQ) {
                throw new QueryDeclarationException(descriptor.getModelName(), descriptor.getMethodName(),
                        predicate.getFieldName(), "unsupported operator " + predicate.getOperator());
            }
            if (!QueryIdentifiers.isJavaIdentifier(predicate.getFieldName())
                    || !QueryIdentifiers.isJavaIdentifier(predicate.getParameterName())) {
                throw new QueryDeclarationException(descriptor.getModelName(), descriptor.getMethodName(),
                        predicate.getFieldName(), "refusing to render an unvalidated field");
            }
            if (i > 0) {
                jpql.append(" AND ");
            }
            jpql.append("e.").append(predicate.getFieldName()).append(' ');
            if (nullValues[i]) {
                if (predicate.isPrimitive()) {
                    throw new QueryDeclarationException(descriptor.getModelName(), descriptor.getMethodName(),
                            predicate.getFieldName(), "primitive field does not accept null");
                }
                jpql.append("IS NULL");
            } else {
                jpql.append("= :").append(predicate.getParameterName());
            }
        }
        if (!descriptor.getSorts().isEmpty()) {
            jpql.append(" ORDER BY ");
            for (int i = 0; i < descriptor.getSorts().size(); i++) {
                QuerySort sort = descriptor.getSorts().get(i);
                if (!QueryIdentifiers.isJavaIdentifier(sort.getFieldName()) || sort.getDirection() == null) {
                    throw new QueryDeclarationException(descriptor.getModelName(), descriptor.getMethodName(),
                            sort.getFieldName(), "refusing to render an unvalidated sort");
                }
                if (i > 0) {
                    jpql.append(", ");
                }
                jpql.append("e.").append(sort.getFieldName()).append(' ').append(sort.getDirection().name());
            }
        }
        return jpql.toString();
    }

    private static final class ListView {
        private final int size;

        private ListView(QueryDescriptor descriptor) {
            this.size = descriptor.getPredicates().size();
        }
    }
}
