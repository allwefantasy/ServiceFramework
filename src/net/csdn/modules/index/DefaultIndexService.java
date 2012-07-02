package net.csdn.modules.index;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import net.csdn.cluster.routing.Routing;
import net.csdn.cluster.routing.Shard;
import net.csdn.common.path.Url;
import net.csdn.common.settings.Settings;
import net.csdn.modules.analyzer.AnalyzerService;
import net.csdn.modules.gateway.GatewayService;
import net.csdn.modules.http.RestController;
import net.csdn.modules.http.support.HttpStatus;
import net.csdn.modules.gateway.GatewayData;
import net.csdn.modules.threadpool.ThreadPoolService;
import net.csdn.modules.transport.HttpTransportService;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.lucene.index.Engine;
import org.apache.lucene.index.IndexEngine;

import java.io.IOException;
import java.util.*;

import static net.csdn.modules.http.RestRequest.Method.PUT;

/**
 * User: WilliamZhu
 * Date: 12-6-7
 * Time: 上午9:54
 */
public class DefaultIndexService implements IndexService {
    @Inject
    protected Settings settings;

    @Inject
    protected GatewayData gatewayData;

    @Inject
    protected GatewayService gatewayService;

    @Inject
    protected HttpTransportService transportService;
    @Inject
    protected ThreadPoolService threadPoolService;
    @Inject
    protected RestController controller;
    @Inject
    protected AnalyzerService analyzerService;


    public IndexService deleteIndex(String index) {
        Routing routing = gatewayData.routing(index);
        if (routing == null) return this;
        for (Shard shard : routing.shards()) {
            if (shard.local())
                shard.engineFutureTask().safeClose();
        }
        gatewayService.removeRouting(index);
        gatewayService.removeMapping(index);
        gatewayService.persist();

        return this;
    }

    @Override
    public IndexService flushIndex(String index) {
        List<Engine> engines = engines(index);
        for (Engine engine : engines) {
            engine.flush(false);
        }
        return this;
    }


    public List<Engine> engines(final String index) {
        List<Engine> engines = new ArrayList<Engine>();
        Routing routing = gatewayData.routing(index);
        for (Shard shard : routing.shards()) {
            if (shard.local()) {
                engines.add(shard.engineFutureTask());
            }
        }
        return engines;

    }

    @Override
    public IndexService refreshIndex(String index) {
        List<Engine> engines = engines(index);
        for (Engine engine : engines) {
            engine.refresh();
        }
        return this;
    }


    @Override
    public boolean bulkIndex(String index, String type, String bulkIndexDataStr, List<Url> otherHosts) {


        if (otherHosts == null) {
            JSONObject bulkIndexDataFromServer = JSONObject.fromObject(bulkIndexDataStr);
            _innerBulkIndex(gatewayData.routing(index).findShard(bulkIndexDataFromServer.getInt("shardId")), type, Lists.newArrayList(bulkIndexDataFromServer.getJSONArray("data")));
            return false;
        }
        JSONArray bulkIndexData = JSONArray.fromObject(bulkIndexDataStr);
        Map<Shard, List<JSONObject>> classifies = Maps.newHashMap();
        boolean acknowledged = false;
        for (int i = 0; i < bulkIndexData.size(); i++) {
            JSONObject data = bulkIndexData.getJSONObject(i);
            String uid = type + "#" + data.getInt("id");

            Shard targetShard = gatewayData.routing(index).shard(uid);

            List<JSONObject> temp = classifies.get(targetShard);
            if (temp == null) {
                classifies.put(targetShard, Lists.<JSONObject>newArrayList(data));
            } else {
                temp.add(data);
            }
        }

        for (Map.Entry<Shard, List<JSONObject>> entry : classifies.entrySet()) {
            Shard shard = entry.getKey();
            List<JSONObject> temp = entry.getValue();

            if (gatewayData.isInMasterSlaveModel() && gatewayData.getLocalHost().isMaster()) {
                _innerBulkIndex(shard, type, temp);
            } else {
                //处理本地的
                if (shard.host().equals(gatewayData.getLocalHost().hostAndPort())) {
                    _innerBulkIndex(shard, type, temp);
                }
                //发给其他服务器处理
                else {
                    if (otherHosts.size() == 0) continue;
                    JSONArray jsonArray = JSONArray.fromObject(temp);
                    JSONObject obj = new JSONObject();
                    obj.put("data", jsonArray);
                    obj.put("shardId", shard.shardId());

                    Url oldUrl = otherHosts.get(0);
                    Url url = new Url().hostAndPort(shard.host()).path(oldUrl.getPath()).query(oldUrl.getQuery());
                    HttpTransportService.SResponse result = transportService.http(url, obj.toString(), PUT);
                    if (result != null && result.getStatus() == HttpStatus.HttpStatusOK) {
                        acknowledged = true;
                    }
                }

            }

        }
        return acknowledged;

    }

    private void _innerBulkIndex(Shard shard, String type, List<JSONObject> data) {
        int length = data.size();
        Engine indexEngine = shard.engineFutureTask();
        Engine.Mapper mapper = gatewayData.mapper(shard.index(), type);
        for (int i = 0; i < length; i++) {
            JSONObject temp = data.get(i);
            try {
                indexEngine.create(IndexEngine.newCreate(mapper, temp), false);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
