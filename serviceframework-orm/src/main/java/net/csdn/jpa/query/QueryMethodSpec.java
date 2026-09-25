package net.csdn.jpa.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Raw declaration before field resolution. Annotation mirrors and reflection
 * both produce this shape; {@link QuerySchemas} is the only validator.
 */
final class QueryMethodSpec {

    private final String name;
    private final List<String> fields;
    private final List<String> orderBy;

    QueryMethodSpec(String name, List<String> fields, List<String> orderBy) {
        this.name = name;
        this.fields = Collections.unmodifiableList(new ArrayList<String>(fields));
        this.orderBy = Collections.unmodifiableList(new ArrayList<String>(orderBy));
    }

    String getName() {
        return name;
    }

    List<String> getFields() {
        return fields;
    }

    List<String> getOrderBy() {
        return orderBy;
    }
}
