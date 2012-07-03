package net.csdn.validate;

/**
 * User: WilliamZhu
 * Date: 12-7-3
 * Time: 下午6:24
 */
public class ValidateHelper {
    public static String length = "length";
    public static String presence = "presence";

    public static class Presence {
        public static String message = "message";
    }

    public static class Length {
        public static String minimum = "minimum";
        public static String maximum = "maximum";
        public static String too_short = "too_short";
        public static String too_long = "too_long";
    }
}
