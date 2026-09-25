package net.csdn.jpa.query;

/**
 * Operators the limited grammar can render.
 * Only equality is accepted. Null equality is rendered as {@code IS NULL}
 * by {@link QueryJpql}; it is not a second operator a declaration can name.
 */
public enum QueryOperator {
    EQ
}
