package net.csdn.common.enhancer;

/**
 * Locatable enhancement failure. Callers must not catch this and continue with a null class.
 */
public class EnhancementFailure extends RuntimeException {

    public enum Category {
        CONFIGURATION,
        CONFLICT,
        DEFINITION,
        DEPENDENCY,
        SCAN,
        ENHANCEMENT,
        LIFECYCLE,
        UNSUPPORTED
    }

    private final Category category;
    private final String className;
    private final String ruleId;
    private final String phase;
    private final String detail;

    public EnhancementFailure(Category category, String className, String ruleId, String phase, String detail, Throwable cause) {
        super(cause);
        if (category == null) {
            throw new IllegalArgumentException("category is required");
        }
        this.category = category;
        this.className = className;
        this.ruleId = ruleId;
        this.phase = phase;
        this.detail = detail == null ? "" : detail;
    }

    public Category getCategory() {
        return category;
    }

    public String getClassName() {
        return className;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getPhase() {
        return phase;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public String getMessage() {
        return "[enhancement " + category + "]"
                + " phase=" + dash(phase)
                + " class=" + dash(className)
                + " rule=" + dash(ruleId)
                + (detail.length() == 0 ? "" : " " + detail);
    }

    private static String dash(String value) {
        return value == null || value.length() == 0 ? "-" : value;
    }
}
