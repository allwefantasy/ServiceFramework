package net.csdn.modules.persist.mongodb;

import net.csdn.modules.transport.data.SearchHit;

import java.util.List;
import java.util.Map;

/**
 * User: WilliamZhu
 * Date: 12-6-9
 * Time: 上午10:00
 */
public interface MongoService {
    void fetchData(List<SearchHit> searchHitList);

    public Map fetchOne(String tableName, int id);
}
