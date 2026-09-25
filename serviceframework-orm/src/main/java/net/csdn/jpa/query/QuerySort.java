package net.csdn.jpa.query;

import java.util.Objects;

public final class QuerySort {

    private final String fieldName;
    private final QueryDirection direction;

    public QuerySort(String fieldName, QueryDirection direction) {
        this.fieldName = fieldName;
        this.direction = direction;
    }

    public String getFieldName() {
        return fieldName;
    }

    public QueryDirection getDirection() {
        return direction;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof QuerySort)) {
            return false;
        }
        QuerySort that = (QuerySort) other;
        return Objects.equals(fieldName, that.fieldName) && direction == that.direction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName, direction);
    }

    @Override
    public String toString() {
        return fieldName + " " + direction;
    }
}
