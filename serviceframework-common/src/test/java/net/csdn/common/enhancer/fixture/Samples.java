package net.csdn.common.enhancer.fixture;

import java.util.HashMap;
import java.util.Map;

public final class Samples {

    private Samples() {
    }

    public static class FinalGetterParent {
        public String name = "parent-field";

        public final String getName() {
            return "parent-method";
        }
    }

    public static class OpenGetterParent {
        public String name = "parent-field";

        public String getName() {
            return "parent-method";
        }
    }

    public static class StaticGetterParent {
        public static String getName() {
            return "static-name";
        }
    }

    public static class IntegerGetterParent {
        public Integer getName() {
            return Integer.valueOf(1);
        }
    }

    public static class ParentStatic {
        @FieldMarker("kept")
        public static final String parent$_code = "CONST-VALUE";
        public static final int parent$_n = 7;
        public static Map<String, Integer> parent$_typed;
        public static String parent$_label;
        public static int parent$_counter;
        public static Map parent$_map;

        static {
            parent$_label = "run" + "time";
            parent$_counter = 5;
        }

        public static Map parent$_map() {
            if (parent$_map == null) {
                parent$_map = new HashMap();
            }
            return parent$_map;
        }
    }
}
