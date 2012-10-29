package net.csdn.mongo.commands;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import net.csdn.common.reflect.ReflectHelper;
import net.csdn.mongo.Document;

/**
 * User: WilliamZhu
 * Date: 12-10-17
 * Time: 下午2:07
 */
public class Delete {


    public static boolean execute(Document doc) {

        Document parent = doc._parent;

        if (parent != null) {
            parent.remove(doc);
        } else {
            //we cannot call doc.collection().remove() directly,because of the dam inheritance of static methods in java
            DBCollection collection = (DBCollection) ReflectHelper.staticMethod(doc.getClass(), "collection");
            collection.remove(new BasicDBObject("_id", doc.id()));
        }

        return true;
    }
}
