package net.csdn.jpa.query;

/**
 * Hard bounds for the limited companion-query grammar.
 * These bounds are shared by annotation processing and runtime validation.
 */
public final class QueryLimits {

    public static final int MAX_METHODS = 8;
    public static final int MAX_FIELDS = 4;
    public static final int MAX_ORDER_FIELDS = 3;
    public static final int MAX_METHOD_NAME_LENGTH = 160;
    public static final int MAX_FIELD_NAME_LENGTH = 80;
    public static final int MAX_GENERATED_SOURCE_CHARS = 4500;
    public static final int MAX_PAGE_SIZE = 500;

    private QueryLimits() {
    }
}
