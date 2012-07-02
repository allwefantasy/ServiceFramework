package net.csdn.modules.persist;

import com.google.inject.Inject;
import net.csdn.common.settings.Settings;
import net.csdn.modules.persist.mongodb.MongoService;
import net.csdn.modules.persist.mysql.MysqlService;
import net.csdn.modules.search.SearchResult;
import net.csdn.modules.threadpool.ThreadPoolService;
import net.csdn.modules.transport.data.SearchHit;
import net.sf.json.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * User: WilliamZhu
 * Date: 12-6-18
 * Time: 下午9:18
 */
public class PersistService {
    @Inject
    private MysqlService mysqlService;
    @Inject
    private MongoService mongoService;

    @Inject
    private ThreadPoolService threadPoolService;

    @Inject
    private Settings settings;


    public void fillData(final SearchResult searchResult) {
        threadPoolService.runWithTimeout(1000, new ThreadPoolService.Run<Object>() {
            @Override
            public Object run() {
                try {
                    if (settings.get("search.persist", "mysql").equals("mysql")) {
                        mysql(searchResult.getDatas());
                    } else {
                        mongoService.fetchData(searchResult.getDatas());
                    }
                } catch (Exception e) {
                    //如果数据库出错，忽略
                }
                return null;
            }
        });
    }

    private void mysql(List<SearchHit> searchHits) {
        if (searchHits.size() == 0) return;

        for (SearchHit searchHit : searchHits) {
            String tableName = searchHit.get_index();
            if (tableName.equals("news"))
                tableName = "news";
            else tableName = tableName + "s";
            Map result = mysqlService.single_query("select content from " + tableName + " where id=?", searchHit.get_id());
            if (result == null) continue;
            Map map = searchHit.getObject();
            map.putAll(JSONObject.fromObject(result.get("content")));
        }
    }

}
