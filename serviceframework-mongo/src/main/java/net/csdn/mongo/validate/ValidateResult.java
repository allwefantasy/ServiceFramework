package net.csdn.mongo.validate;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-3
 * Time: 下午6:52
 */
public class ValidateResult {
    private String message;
    private String fieldName;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public ValidateResult(String message, String fieldName) {
        this.message = message;
        this.fieldName = fieldName;
    }
}
