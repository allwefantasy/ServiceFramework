package net.csdn.modules.persist.mongodb;

import com.google.inject.Inject;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import net.csdn.modules.transport.data.SearchHit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: WilliamZhu
 * Date: 12-6-9
 * Time: 上午9:45
 */
public class DefaultMongoService implements MongoService {

    @Inject
    private MongoClient mongoClient;

//    public void fetchData(List<SearchHit> searchHitList) {
//        if (searchHitList.size() == 0) return;
//        DBObject query = new BasicDBObject();
//        BasicDBList lists = new BasicDBList();
//
//        for (SearchHit searchHit : searchHitList) {
//            lists.add(searchHit.get_id());
//        }
//        query.put("_id", new BasicDBObject("$in", lists));
//        List<DBObject> results = mongoClient.query(searchHitList.get(0).get_index(), query);
//        for (DBObject object : results) {
//
//        }
//    }

    @Override
    public void fetchData(List<SearchHit> searchHitList) {
        if (searchHitList.size() == 0) return;

        for (SearchHit searchHit : searchHitList) {
            DBObject dbObject = mongoClient.queryOne(searchHit.get_index(), new BasicDBObject("_id", Integer.parseInt(searchHit.get_id())));
            Map map = searchHit.getObject();
            for (String key : dbObject.keySet()) {
                map.put(key, dbObject.get(key));
            }
        }

    }

    @Override
    public Map fetchOne(String tableName, int id) {
        DBObject query = new BasicDBObject("_id", id);
        DBObject dbObject = mongoClient.queryOne(tableName, query);
        return dbObjectToMap(dbObject);
    }

    private Map dbObjectToMap(DBObject dbObject) {
        Map result = new HashMap();
        for (String key : dbObject.keySet()) {
            result.put(key, dbObject.get(key));
        }
        return result;
    }
}
