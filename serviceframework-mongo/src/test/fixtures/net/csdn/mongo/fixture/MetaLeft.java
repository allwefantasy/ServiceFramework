package net.csdn.mongo.fixture;

import net.csdn.mongo.Document;

public class MetaLeft extends Document {
    static {
        storeIn(Names.of("meta_left"));
        alias("name", "leftAlias");
    }

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
