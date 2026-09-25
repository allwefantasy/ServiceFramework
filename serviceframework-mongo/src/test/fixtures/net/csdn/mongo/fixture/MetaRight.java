package net.csdn.mongo.fixture;

import net.csdn.mongo.Document;

public class MetaRight extends Document {
    static {
        storeIn(Names.of("meta_right"));
        alias("name", "rightAlias");
    }

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
