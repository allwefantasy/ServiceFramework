package net.csdn.modules.persist.mongodb;

import com.google.inject.Inject;
import com.mongodb.*;
import net.csdn.common.settings.Settings;
import net.csdn.modules.cache.RedisClient;
import net.sf.json.JSONObject;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-9
 * Time: 上午8:47
 */
public class MongoClient {

    private Mongo mongo;
    private Settings settings;
    private String dbName;
    @Inject
    private RedisClient redisClient;

    @Inject
    public MongoClient(Settings _settings) {
        this.settings = _settings;
        dbName = settings.get("mongo.database", "csdn_data_center");
        try {
            this.mongo = new Mongo(settings.get("mongo.host", "127.0.0.1"), settings.getAsInt("mongo.port", 27017));

        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public DBCollection dbCollection(String tableName) {
        return mongo.getDB(dbName).getCollection(tableName);
    }

    public MongoClient destroy(String table, DBObject dbObject) {
        dbCollection(table).remove(dbObject);
        return this;
    }

    public MongoClient batchInsert(String tableName, List<JSONObject> objectList) {
        List<DBObject> dbObjects = new ArrayList<DBObject>(objectList.size());
        for (JSONObject object : objectList) {
            DBObject dbObject = new BasicDBObject();
            dbObject.put("_id", object.getInt("id"));
            dbObject.putAll(object);
            dbObjects.add(dbObject);
        }
        dbCollection(tableName).insert(dbObjects);
        return this;
    }


    public List<DBObject> query(String tableName, DBObject dbObject) {
        List<DBObject> objects = new ArrayList<DBObject>();
        DBCursor cursor = dbCollection(tableName).find(dbObject);
        while (cursor.hasNext()) {
            objects.add(cursor.next());
        }
        return objects;
    }

    public DBObject queryOne(String tableName, DBObject dbObject) {
        try {
            if (settings.getAsBoolean("cache.enable", false)) {
                String key = tableName + ":" + dbObject.get("_id");
                String value = redisClient.get(key);
                if (value == null) {
                    DBObject temp = dbCollection(tableName).findOne(dbObject);
                    redisClient.set(key, JSONObject.fromObject(temp).toString());
                    return temp;
                }
                DBObject dbObject1 = new BasicDBObject();
                dbObject1.putAll(JSONObject.fromObject(value));
                return dbObject1;
            }
        } catch (Exception e) {
            //ignore
        }
        return dbCollection(tableName).findOne(dbObject);
    }


    public void shutdown() {
        mongo.close();
    }

}
