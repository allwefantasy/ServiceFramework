package net.csdn.bootstrap.lifecycle.web.db.mongo;

import net.csdn.mongo.Document;

public class WebNote extends Document {
    static {
        storeIn("sf_web_lifecycle");
    }

    private String text;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
