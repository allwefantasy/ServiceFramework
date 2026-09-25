package net.csdn.jpa.query;

import java.util.Objects;

/**
 * One equality predicate. The parameter name is assigned by the grammar
 * ({@code p0}, {@code p1}, ...) and is never taken from user input.
 */
public final class QueryPredicate {

    private final String fieldName;
    private final String javaType;
    private final boolean primitive;
    private final QueryOperator operator;
    private final String parameterName;

    public QueryPredicate(String fieldName, String javaType, boolean primitive, QueryOperator operator, String parameterName) {
        this.fieldName = fieldName;
        this.javaType = javaType;
        this.primitive = primitive;
        this.operator = operator;
        this.parameterName = parameterName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getJavaType() {
        return javaType;
    }

    public boolean isPrimitive() {
        return primitive;
    }

    public QueryOperator getOperator() {
        return operator;
    }

    public String getParameterName() {
        return parameterName;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof QueryPredicate)) {
            return false;
        }
        QueryPredicate that = (QueryPredicate) other;
        return primitive == that.primitive
                && Objects.equals(fieldName, that.fieldName)
                && Objects.equals(javaType, that.javaType)
                && operator == that.operator
                && Objects.equals(parameterName, that.parameterName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName, javaType, Boolean.valueOf(primitive), operator, parameterName);
    }

    @Override
    public String toString() {
        return fieldName + " " + operator + " :" + parameterName + " (" + javaType + ")";
    }
}
