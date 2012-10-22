package com.example.document;

import net.csdn.mongo.Document;

/**
 * User: WilliamZhu
 * Date: 12-10-18
 * Time: 下午2:50
 */
public class Address extends Document {
    static {
        storeIn("addresses");
    }
}
