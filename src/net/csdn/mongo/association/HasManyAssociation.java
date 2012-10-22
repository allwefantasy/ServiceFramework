package net.csdn.mongo.association;

import net.csdn.mongo.Document;

import java.util.List;

import static net.csdn.common.collections.WowCollections.list;

/**
 * User: WilliamZhu
 * Date: 12-10-17
 * Time: 上午10:09
 */
public class HasManyAssociation {
    private List<Document> documents = list();

    private Class kclass;
    private Document parent;
    private Object foreignKey;


    public HasManyAssociation(Document document,Options options) {
         kclass = options.kClass();
         parent = document;
         foreignKey = options.foreignKey();
         //documents =
    }
}
