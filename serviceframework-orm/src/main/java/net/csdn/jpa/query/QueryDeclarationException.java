package net.csdn.jpa.query;

/**
 * A query declaration failed shared validation.
 * The message always names the model, method and field so build-time
 * diagnostics and runtime failures stay specific.
 */
public class QueryDeclarationException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final String modelName;
    private final String methodName;
    private final String fieldName;

    public QueryDeclarationException(String modelName, String methodName, String fieldName, String reason) {
        super(diagnostic(modelName, methodName, fieldName, reason));
        this.modelName = blank(modelName);
        this.methodName = blank(methodName);
        this.fieldName = blank(fieldName);
    }

    public String getModelName() {
        return modelName;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public static String diagnostic(String modelName, String methodName, String fieldName, String reason) {
        return "ServiceFramework query declaration failed: model " + blank(modelName)
                + " method " + blank(methodName)
                + " field " + blank(fieldName)
                + ": " + (reason == null ? "" : reason);
    }

    private static String blank(String value) {
        return value == null || value.length() == 0 ? "-" : value;
    }
}
