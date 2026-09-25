package net.csdn.mongo.fixture;

import net.csdn.mongo.Document;

public class Note extends Document {
    static {
        storeIn(Names.of("note"));
    }

    private String text;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
