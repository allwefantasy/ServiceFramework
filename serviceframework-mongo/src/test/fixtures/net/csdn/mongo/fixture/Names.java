package net.csdn.mongo.fixture;

public final class Names {
    private Names() {
    }

    public static String of(String suffix) {
        String prefix = System.getProperty("sf.mongo.collection.prefix");
        if (prefix == null || !prefix.startsWith("sf_it_")) {
            throw new IllegalStateException("refusing a collection outside the sf_it_ prefix");
        }
        return prefix + "_" + suffix;
    }
}
