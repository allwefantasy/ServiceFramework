package net.csdn.mongo.commands;

import com.mongodb.DBCollection;
import net.csdn.mongo.Document;
import net.csdn.reflect.ReflectHelper;

/**
 * User: WilliamZhu
 * Date: 12-10-17
 * Time: 下午2:07
 */
public class Save {

    public static boolean execute(Document doc, boolean validate) {
        Class clzz = doc.getClass();
        Document parent = doc._parent;
        if (parent != null) {
            Save.execute(parent, validate);
        } else {
            DBCollection collection = (DBCollection) ReflectHelper.staticMethod(clzz, "collection");
            collection.save(doc.attributes());
        }
        return true;
    }
}
