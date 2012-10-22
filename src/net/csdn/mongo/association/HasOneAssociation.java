package net.csdn.mongo.association;

import net.csdn.mongo.Document;

/**
 * User: WilliamZhu
 * Date: 12-10-17
 * Time: 上午10:09
 */
public class HasOneAssociation {
    private Class kclass;
    private Document parent;
    private Object foreignKey;

    public HasOneAssociation(Document document,Options options) {
        kclass = options.kClass();
        parent = document;
        foreignKey = options.foreignKey();
    }
}
