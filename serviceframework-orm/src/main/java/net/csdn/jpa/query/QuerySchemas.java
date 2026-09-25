package net.csdn.jpa.query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Single declaration validator for annotation processing and runtime.
 * JPQL is rendered only by {@link QueryJpql} from the descriptor this class returns.
 */
final class QuerySchemas {

    private static final Set<String> SCALARS = scalars();

    private QuerySchemas() {
    }

    static QueryMetadata compile(QueryModelView model, List<QueryMethodSpec> specs) {
        List<QueryDeclarationException> errors = new ArrayList<QueryDeclarationException>();
        QueryMetadata metadata = compile(model, specs, errors);
        if (!errors.isEmpty()) {
            throw errors.get(0);
        }
        return metadata;
    }

    static QueryMetadata compile(QueryModelView model, List<QueryMethodSpec> specs, List<QueryDeclarationException> errors) {
        String modelName = model == null ? "-" : model.getModelName();
        if (model == null || modelName == null || modelName.length() == 0 || "-".equals(modelName)) {
            errors.add(new QueryDeclarationException(modelName, "-", "-", "model name is missing"));
            return null;
        }
        if (specs == null || specs.isEmpty()) {
            errors.add(new QueryDeclarationException(modelName, "-", "-", "at least one @QueryMethod is required"));
            return null;
        }
        if (specs.size() > QueryLimits.MAX_METHODS) {
            errors.add(new QueryDeclarationException(modelName, "-", "-",
                    "method count " + specs.size() + " exceeds " + QueryLimits.MAX_METHODS));
            return null;
        }
        List<QueryDescriptor> methods = new ArrayList<QueryDescriptor>();
        Set<String> names = new HashSet<String>();
        for (int i = 0; i < specs.size(); i++) {
            try {
                methods.add(compileMethod(model, specs.get(i), names));
            } catch (QueryDeclarationException ex) {
                errors.add(ex);
            }
        }
        if (!errors.isEmpty()) {
            return null;
        }
        return new QueryMetadata(modelName, methods);
    }

    private static QueryDescriptor compileMethod(QueryModelView model, QueryMethodSpec spec, Set<String> names) {
        String modelName = model.getModelName();
        String methodName = spec == null ? null : spec.getName();
        if (spec == null) {
            throw new QueryDeclarationException(modelName, "-", "-", "@QueryMethod is missing");
        }
        validateMethodName(modelName, methodName);
        if (!names.add(methodName)) {
            throw new QueryDeclarationException(modelName, methodName, "-", "duplicate query method name");
        }
        List<String> fields = spec.getFields();
        if (fields == null || fields.isEmpty()) {
            throw new QueryDeclarationException(modelName, methodName, "-", "at least one equality field is required");
        }
        if (fields.size() > QueryLimits.MAX_FIELDS) {
            throw new QueryDeclarationException(modelName, methodName, "-",
                    "field count " + fields.size() + " exceeds " + QueryLimits.MAX_FIELDS);
        }
        List<QueryPredicate> predicates = new ArrayList<QueryPredicate>();
        Set<String> seenFields = new HashSet<String>();
        for (int i = 0; i < fields.size(); i++) {
            String fieldName = fields.get(i);
            if (fieldName != null && !seenFields.add(fieldName)) {
                throw new QueryDeclarationException(modelName, methodName, fieldName, "duplicate equality field");
            }
            QueryFieldView field = resolveField(model, methodName, fieldName);
            if (field.isEnum() == false && !SCALARS.contains(field.getJavaType())) {
                throw new QueryDeclarationException(modelName, methodName, fieldName,
                        "unsupported field type " + field.getJavaType()
                                + "; limited grammar allows scalar equality and enums only, not DTO, association, collection or java.time values");
            }
            predicates.add(new QueryPredicate(field.getName(), field.getJavaType(), field.isPrimitive(), QueryOperator.EQ, "p" + i));
        }
        List<QuerySort> sorts = compileSorts(model, methodName, spec.getOrderBy());
        return new QueryDescriptor(modelName, methodName, predicates, sorts);
    }

    private static void validateMethodName(String modelName, String methodName) {
        if (methodName == null || methodName.length() == 0) {
            throw new QueryDeclarationException(modelName, "-", "-", "query method name is empty");
        }
        if (methodName.length() > QueryLimits.MAX_METHOD_NAME_LENGTH) {
            throw new QueryDeclarationException(modelName, methodName, "-",
                    "method name length " + methodName.length() + " exceeds " + QueryLimits.MAX_METHOD_NAME_LENGTH);
        }
        if (!QueryIdentifiers.isJavaIdentifier(methodName) || QueryIdentifiers.isJavaKeyword(methodName)) {
            throw new QueryDeclarationException(modelName, methodName, "-", "query method name is not a Java identifier");
        }
    }

    private static List<QuerySort> compileSorts(QueryModelView model, String methodName, List<String> orderBy) {
        if (orderBy == null || orderBy.isEmpty()) {
            return new ArrayList<QuerySort>();
        }
        if (orderBy.size() > QueryLimits.MAX_ORDER_FIELDS) {
            throw new QueryDeclarationException(model.getModelName(), methodName, "-",
                    "orderBy count " + orderBy.size() + " exceeds " + QueryLimits.MAX_ORDER_FIELDS);
        }
        List<QuerySort> sorts = new ArrayList<QuerySort>();
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < orderBy.size(); i++) {
            ParsedOrder parsed = parseOrder(model.getModelName(), methodName, orderBy.get(i));
            if (!seen.add(parsed.fieldName)) {
                throw new QueryDeclarationException(model.getModelName(), methodName, parsed.fieldName, "duplicate order field");
            }
            resolveField(model, methodName, parsed.fieldName);
            sorts.add(new QuerySort(parsed.fieldName, parsed.direction));
        }
        return sorts;
    }

    private static ParsedOrder parseOrder(String modelName, String methodName, String raw) {
        if (raw == null || raw.trim().length() == 0) {
            throw new QueryDeclarationException(modelName, methodName, raw, "order token is empty");
        }
        if (raw.length() > 120) {
            throw new QueryDeclarationException(modelName, methodName, raw, "order token is too long");
        }
        String[] parts = raw.trim().split("\\s+");
        if (parts.length > 2) {
            throw new QueryDeclarationException(modelName, methodName, raw,
                    "order token must be 'field', 'field asc' or 'field desc'");
        }
        QueryDirection direction = QueryDirection.ASC;
        if (parts.length == 2) {
            String token = parts[1].toLowerCase(Locale.ENGLISH);
            if ("asc".equals(token)) {
                direction = QueryDirection.ASC;
            } else if ("desc".equals(token)) {
                direction = QueryDirection.DESC;
            } else {
                throw new QueryDeclarationException(modelName, methodName, parts[0],
                        "unsupported order direction '" + parts[1] + "'");
            }
        }
        return new ParsedOrder(parts[0], direction);
    }

    static QueryFieldView resolveField(QueryModelView model, String methodName, String fieldName) {
        String modelName = model.getModelName();
        if (fieldName == null || fieldName.length() == 0) {
            throw new QueryDeclarationException(modelName, methodName, fieldName, "field name is empty");
        }
        if (fieldName.length() > QueryLimits.MAX_FIELD_NAME_LENGTH
                || !QueryIdentifiers.isJavaIdentifier(fieldName)
                || QueryIdentifiers.isJavaKeyword(fieldName)) {
            throw new QueryDeclarationException(modelName, methodName, fieldName,
                    "field is not an allowed Java identifier");
        }
        if (QueryIdentifiers.isJpqlReserved(fieldName)) {
            throw new QueryDeclarationException(modelName, methodName, fieldName,
                    "field identifier is reserved by the limited JPQL grammar");
        }
        QueryFieldView field = model.findField(fieldName);
        if (field == null) {
            throw new QueryDeclarationException(modelName, methodName, fieldName,
                    "no declared field; inherited framework fields on Model and JPABase are not searchable, and a missing name is not inferred");
        }
        if (!field.isEligible()) {
            throw new QueryDeclarationException(modelName, methodName, fieldName, field.getIneligibleReason());
        }
        if (!field.isSupportedScalar() && !field.isEnum()) {
            throw new QueryDeclarationException(modelName, methodName, fieldName,
                    "unsupported field type " + field.getJavaType());
        }
        return field;
    }

    static boolean isSupportedScalarName(String javaType) {
        return SCALARS.contains(javaType);
    }

    private static Set<String> scalars() {
        HashSet<String> set = new HashSet<String>();
        String[] values = new String[]{
                "boolean", "byte", "short", "int", "long", "char", "float", "double",
                "java.lang.Boolean", "java.lang.Byte", "java.lang.Short", "java.lang.Integer",
                "java.lang.Long", "java.lang.Character", "java.lang.Float", "java.lang.Double",
                "java.lang.String",
                "java.util.Date", "java.sql.Date", "java.sql.Time", "java.sql.Timestamp",
                "java.math.BigDecimal", "java.math.BigInteger", "java.util.UUID"
        };
        for (int i = 0; i < values.length; i++) {
            set.add(values[i]);
        }
        return set;
    }

    private static final class ParsedOrder {
        private final String fieldName;
        private final QueryDirection direction;

        private ParsedOrder(String fieldName, QueryDirection direction) {
            this.fieldName = fieldName;
            this.direction = direction;
        }
    }
}
