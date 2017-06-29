package net.csdn.mongo.validate;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-3
 * Time: 下午6:24
 */
public class ValidateHelper {
    public static String length = "length";
    public static String presence = "presence";
    public static String numericality = "numericality";
    public static String message = "message";
    public static String uniqueness = "uniqueness";
    public static String format = "format";
    public static String associated = "associated";

    public static class Associated {

    }


    public static class Format {
        public static String with = "with";
    }

    public static class Numericality {
        public static String greater_than = "greater_than";
        public static String greater_than_or_equal_to = "greater_than_or_equal_to";
        public static String equal_to = "equal_to";
        public static String less_than = "less_than";
        public static String less_than_or_equal_to = "less_than_or_equal_to";
        public static String odd = "odd";
        public static String even = "even";

        public static boolean odd(Integer d1) {
            return d1 % 2 != 0;
        }

        public static boolean even(Integer d1) {
            return d1 % 2 == 0;
        }

        public static boolean greater_than(Comparable d1, Comparable d2) {
            return d1.compareTo(d2) > 0;
        }

        public static boolean greater_than_or_equal_to(Comparable d1, Comparable d2) {
            return d1.compareTo(d2) > 0 || d1.compareTo(d2) == 0;
        }

        public static boolean equal_to(Comparable d1, Comparable d2) {
            return d1.compareTo(d2) == 0;
        }

        public static boolean less_than(Comparable d1, Comparable d2) {
            return d1.compareTo(d2) < 0;
        }

        public static boolean less_than_or_equal_to(Comparable d1, Comparable d2) {
            return d1.compareTo(d2) < 0 || d1.compareTo(d2) == 0;
        }


    }

    public static class Length {
        public static String minimum = "minimum";
        public static String maximum = "maximum";
        public static String too_short = "too_short";
        public static String too_long = "too_long";
    }
}
