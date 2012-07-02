package net.csdn.modules.gateway;

import com.google.inject.Inject;
import net.csdn.cluster.routing.Routing;
import net.csdn.cluster.routing.Shard;
import net.csdn.common.io.Streams;
import net.csdn.common.path.Url;
import net.csdn.env.Environment;
import net.csdn.exception.ArgumentErrorException;
import net.csdn.modules.factory.CommonFactory;
import net.csdn.modules.http.support.HttpStatus;
import net.csdn.modules.transport.HttpTransportService;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.lucene.index.Engine;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.csdn.modules.http.RestRequest.Method.GET;

/**
 * User: WilliamZhu
 * Date: 12-6-2
 * Time: 上午10:54
 */
public class DefaultGatewayService implements GatewayService {

    @Inject
    private Environment environment;
    @Inject
    private GatewayData gatewayData;
    @Inject
    private CommonFactory commonFactory;
    @Inject
    private HttpTransportService transportService;


    //{host1:[0,2,4],host2:[1,3,5]}
    public GatewayService addRouting(String index, String source) {
        JSONObject object = JSONObject.fromObject(source);
        Routing routing = new Routing(index);
        if (gatewayData.isSingleMode()) {
            if (object.size() > 1)
                throw new ArgumentErrorException("目前处于单机模式下,你可以随机写一个别名加上分片。比如{\"ramdonName\":[0,1]}");

            for (Object shardsConfig : object.values()) {
                JSONArray shard_ids = (JSONArray) shardsConfig;
                for (int i = 0; i < shard_ids.size(); i++) {
                    int id = shard_ids.getInt(i);
                    Shard shard = commonFactory.create(id, gatewayData.getLocalHost().hostAndPort(), index);
                    routing.shard(shard);
                }
            }
            gatewayData.getRoutingMapSource().put(index, object.toString());
            gatewayData.getRoutingMap().put(index, routing);
            return this;
        }

        for (Object hostAliasName : object.keySet()) {
            Host wowHost = gatewayData.getAlias_host().get(hostAliasName);

            if (wowHost == null)
                throw new ArgumentErrorException("error when load hostAndPort alias name is [" + hostAliasName + "],please check config file");
            String hostNameAndPort = wowHost.hostAndPort();
            JSONArray shard_ids = object.getJSONArray((String) hostAliasName);
            for (int i = 0; i < shard_ids.size(); i++) {
                int id = shard_ids.getInt(i);
                Shard shard = commonFactory.create(id, hostNameAndPort, index);
                routing.shard(shard);
            }
        }
        gatewayData.getRoutingMapSource().put(index, object.toString());
        gatewayData.getRoutingMap().put(index, routing);
        return this;
    }


    public List<Host> markDownHost(List<Url> urls) {

        List<HttpTransportService.SResponse> responses = transportService.asyncHttps(urls, "{}", GET);
        List<Host> downHosts = new ArrayList<Host>();
        for (HttpTransportService.SResponse response : responses) {
            if (response.getStatus() != HttpStatus.HttpStatusOK) {
                URI uri = response.getUrl().toURI();
                String hostAndPort = uri.getHost() + ":" + uri.getPort();
                for (Host host : gatewayData.otherHosts()) {
                    if (host.hostAndPort().equals(hostAndPort)) {
                        host.setOnline(false);
                        downHosts.add(host);
                    }
                }

            }
        }
        return downHosts;
    }

    public GatewayService addMapping(String index, String type, String source) throws ArgumentErrorException {
        Engine.Mapper newMapper = new Engine.Mapper();
        String key = index + "#" + type;
        gatewayData.getMappingsMapSource().put(key, source);
        gatewayData.getMappingsMap().put(key, newMapper.parse(source));
        return this;
    }

    public GatewayService addMapping(String key, String source) throws ArgumentErrorException {
        Engine.Mapper newMapper = new Engine.Mapper();
        gatewayData.getMappingsMapSource().put(key, source);
        gatewayData.getMappingsMap().put(key, newMapper.parse(source));
        return this;
    }

    public GatewayService removeMapping(String index, String type) {
        String key = index + "#" + type;
        gatewayData.getMappingsMap().remove(key);
        gatewayData.getMappingsMapSource().remove(key);
        return this;
    }

    public GatewayService removeMapping(String index) {
        List<String> mappings_keys = new ArrayList<String>();
        for (Object key : gatewayData.getMappingsMap().keySet()) {
            String mappingKey = (String) key;
            if (mappingKey.startsWith(index + "#")) {
                mappings_keys.add(mappingKey);
            }
        }
        for (String key : mappings_keys) {
            gatewayData.getMappingsMap().remove(key);
            gatewayData.getMappingsMapSource().remove(key);
        }


        return this;
    }

    @Override
    public JSONObject localInfo() {
        JSONObject result = new JSONObject();
        result.put("routing", gatewayData.getRoutingMapSource());
        result.put("mapping", gatewayData.getMappingsMapSource());
        result.put("host", gatewayData.getLocalHost());
        if (gatewayData.isInMasterSlaveModel())
            result.put("master", gatewayData.getMaster());
        return result;
    }

    @Override
    public boolean join(Host hostToJoin) {

        for (Host temp_host : gatewayData.getOtherHosts()) {
            if (temp_host.equals(hostToJoin)) {
                temp_host.setOnline(true);
                if (gatewayData.getMaster() == null && hostToJoin.isMaster()) {
                    gatewayData.setMaster(temp_host);
                    temp_host.setMaster(true);
                }
                return true;
            }
        }
        return false;
    }

    public GatewayService removeRouting(String index) {
        gatewayData.getRoutingMap().remove(index);
        gatewayData.getRoutingMapSource().remove(index);
        return this;
    }


    @Override
    public void persist() {
        Map<String, Map> result = new HashMap<String, Map>();
        try {
            result.put("routing", gatewayData.getRoutingMapSource());
            result.put("mapping", gatewayData.getMappingsMapSource());
            File gatewayDir = environment.gateway();
            if (!gatewayDir.exists()) {
                gatewayDir.mkdirs();
            }

            Streams.copy(JSONObject.fromObject(result).toString(), new FileWriter(environment.gateway() + GatewayData.persistFileName));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void load() {
        try {
            String content = Streams.copyToString(new FileReader(environment.gateway() + GatewayData.persistFileName));
            JSONObject result = JSONObject.fromObject(content);
            //load localHost/otherHosts

            JSONObject routingSource = result.getJSONObject("routing");
            JSONObject mappingSource = result.getJSONObject("mapping");

            for (Object key : routingSource.keySet()) {
                addRouting(key.toString(), routingSource.getString(key.toString()));
                gatewayData.getRoutingMapSource().put(key.toString(), routingSource.getString(key.toString()));
            }

            for (Object key : mappingSource.keySet()) {
                addMapping(key.toString(), mappingSource.getString(key.toString()));
                gatewayData.getMappingsMapSource().put(key.toString(), mappingSource.getString(key.toString()));
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
