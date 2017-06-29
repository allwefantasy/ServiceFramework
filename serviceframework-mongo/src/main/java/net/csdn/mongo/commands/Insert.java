package net.csdn.mongo.commands;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import net.csdn.common.reflect.ReflectHelper;
import net.csdn.mongo.Callbacks;
import net.csdn.mongo.Document;

/**
 * User: WilliamZhu
 * Date: 12-11-6
 * Time: 下午2:34
 */
public class Insert {
    public static boolean execute(Document doc, boolean validate) {
        Document parent = doc._parent;
        if (parent != null) {
            Insert.execute(parent, validate);
        } else {
            doc.runCallbacks(Callbacks.Callback.before_save);
            //we cannot call doc.collection().remove() directly,because of the dam inheritance of static methods in java
            DBCollection collection = (DBCollection) ReflectHelper.staticMethod(doc.getClass(), "collection");
            collection.insert(new BasicDBObject(doc.attributes()));
            doc.runCallbacks(Callbacks.Callback.after_save);
        }

        return true;
    }
}
