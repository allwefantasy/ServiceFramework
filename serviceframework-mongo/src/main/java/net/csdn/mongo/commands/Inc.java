package net.csdn.mongo.commands;

import com.mongodb.DBCollection;
import net.csdn.common.reflect.ReflectHelper;
import net.csdn.mongo.Callbacks;
import net.csdn.mongo.Document;

import java.util.Map;

import static net.csdn.common.collections.WowCollections.map;

/**
 * User: WilliamZhu
 * Date: 12-11-21
 * Time: 下午9:31
 */
public class Inc {
    /*
     @net.csdn.application.document.collection.update(
          @net.csdn.application.document._selector,
          { "$inc" => { field => value } },
          :safe => safe_mode?(@options),
          :multi => false
        )
     */

    private Document doc;


    public Inc() {
    }

    public boolean persist(String field, Object value) {
        Document parent = doc._parent;
        if (parent != null) {
            //Update.execute(parent, validate);
        } else {
            doc.runCallbacks(Callbacks.Callback.before_update);
            //we cannot call doc.collection().remove() directly,because of the dam inheritance of static methods in java
            DBCollection collection = (DBCollection) ReflectHelper.staticMethod(doc.getClass(), "collection");
            Map inc = map("field", "value");
            //collection.update(doc.inc, );
            doc.runCallbacks(Callbacks.Callback.after_update);
        }

        return true;
    }
}
