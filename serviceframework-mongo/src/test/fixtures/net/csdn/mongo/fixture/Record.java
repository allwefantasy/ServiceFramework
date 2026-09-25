package net.csdn.mongo.fixture;

import net.csdn.mongo.Document;

public class Record extends Document {
    static {
        storeIn(Names.of("record"));
        alias("title", "headline");
    }

    private String title;
    private int writes;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title == null) {
            throw new IllegalArgumentException("null title");
        }
        this.title = title.trim();
        writes++;
    }

    public void setTitle(Object title) {
        this.title = String.valueOf(title);
        writes += 10;
    }

    public int getWrites() {
        return writes;
    }
}
