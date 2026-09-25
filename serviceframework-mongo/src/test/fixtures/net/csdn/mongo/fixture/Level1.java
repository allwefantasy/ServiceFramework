package net.csdn.mongo.fixture;

import net.csdn.mongo.Document;

public class Level1 extends Document {
    static {
        storeIn(Names.of("l1"));
        alias("name", "l1");
    }

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
