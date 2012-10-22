package com.example.document;

import net.csdn.mongo.Document;

/**
 * User: WilliamZhu
 * Date: 12-10-18
 * Time: 上午10:31
 */
public class Person extends Document {

    static {
        storeIn("persons");
    }

    private String name;
    private Integer bodyLength;



    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getBodyLength() {
        return bodyLength;
    }

    public void setBodyLength(Integer bodyLength) {
        this.bodyLength = bodyLength;
    }
}
