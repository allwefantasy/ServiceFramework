package net.csdn.mongo.commands;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import net.csdn.common.reflect.ReflectHelper;
import net.csdn.mongo.Callbacks;
import net.csdn.mongo.Document;

/**
 * User: WilliamZhu
 * Date: 12-11-6
 * Time: 下午2:38
 */
public class Update {
    public static boolean execute(Document doc, boolean validate) {
        Document parent = doc._parent;
        if (parent != null) {
            Update.execute(parent, validate);
        } else {
            doc.runCallbacks(Callbacks.Callback.before_update);
            //we cannot call doc.collection().remove() directly,because of the dam inheritance of static methods in java
            if (doc.id() == null) return false;
            DBCollection collection = (DBCollection) ReflectHelper.staticMethod(doc.getClass(), "collection");
            DBObject query = new BasicDBObject("_id", doc.id());
            collection.update(query,new BasicDBObject( doc.attributes()));
            doc.runCallbacks(Callbacks.Callback.after_update);
        }

        return true;
    }
}
