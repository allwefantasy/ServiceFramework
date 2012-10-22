package net.csdn.mongo.commands;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import net.csdn.mongo.Document;
import net.csdn.reflect.ReflectHelper;

/**
 * User: WilliamZhu
 * Date: 12-10-17
 * Time: 下午2:07
 */
public class Delete {
    public static boolean execute(Document doc) {
        Class clzz = doc.getClass();
        DBCollection collection = (DBCollection) ReflectHelper.staticMethod(clzz, "collection");
        collection.remove(new BasicDBObject("_id", doc.id()));
        return true;
    }
}
