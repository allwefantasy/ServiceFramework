package net.csdn.modules.gateway;/**
 * User: WilliamZhu
 * Date: 12-6-1
 * Time: 下午2:20
 */

import com.google.common.collect.Lists;
import net.csdn.cluster.routing.Routing;
import net.csdn.common.network.NetworkUtils;
import net.csdn.common.settings.Settings;
import net.csdn.env.Environment;
import net.csdn.exception.ConfigurationException;
import org.apache.lucene.index.Engine;

import java.util.*;

public class GatewayData {

    public static String persistFileName = "/gateway.json";

    private Host master = null;
    private Host localHost;
    private List<Host> otherHosts;


    private boolean singleMode = false;

    private Map<String, Host> alias_host = new HashMap();

    //index=>routing
    private Map<String, Routing> routingMap = new HashMap<String, Routing>();


    private Map<String, String> routingMapSource = new HashMap<String, String>();

    //index#type => mapping
    private Map<String, Engine.Mapper> mappingsMap = new HashMap<String, Engine.Mapper>();
    private Map<String, String> mappingsMapSource = new HashMap<String, String>();


    private Environment environment;
    private Settings settings;

    public Map<String, String> getMappingsMapSource() {
        return mappingsMapSource;
    }

    public void setMappingsMapSource(Map<String, String> mappingsMapSource) {
        this.mappingsMapSource = mappingsMapSource;
    }

    public Map<String, String> getRoutingMapSource() {
        return routingMapSource;
    }

    public void setRoutingMapSource(Map<String, String> routingMapSource) {
        this.routingMapSource = routingMapSource;
    }

    public GatewayData(Settings settings, Environment environment) {
        this.settings = settings;
        this.environment = environment;

        boolean isMaster = settings.getAsBoolean("master", false);

        checkConfig();

        if (localHost != null) {
            otherHosts = new ArrayList<Host>();
            return;
        }
        String localHostAndPort = findLocal();


        Map<String, String> temps = settings.getByPrefix("host_alias.").getAsMap();
        String localHostKey="";
        for (Map.Entry<String, String> entry : temps.entrySet()) {
            if (entry.getValue().equals(localHostAndPort)) {
                alias_host.put(entry.getKey(), new Host(entry.getValue(), isMaster, true));
                localHostKey = entry.getKey();
            } else {
                alias_host.put(entry.getKey(), new Host(entry.getValue(), false, false));
            }

        }


        this.localHost = alias_host.get(localHostKey);

        List<Host> temp = hosts(settings);
        temp.remove(localHost);
        if (localHost.isMaster()) {
            master = localHost;
        }
        this.otherHosts = temp;


    }

    private void checkConfig() {
        Settings tempSettings = settings.getByPrefix("host_alias.");
        //假设没有任何集群，那么默认启动本机9400端口
        if (tempSettings == null || tempSettings.getAsMap().size() == 0) {
            String localHostName = NetworkUtils.intranet_ip().getHostAddress();
            localHost = new Host(localHostName + ":9400", settings.getAsBoolean("master", false), true);
            singleMode = true;
            return;
        }
        Map<String, String> temps = tempSettings.getAsMap();
        //不允许配置127.0.0.1这种地址
        for (Map.Entry<String, String> entry : temps.entrySet()) {
            if (entry.getValue().contains("127.0.0.1")) {
                throw new ConfigurationException("配置不允许出现127.0.0.1这种地址");
            }
        }
        //获取本地机器
        String localHostName = NetworkUtils.intranet_ip().getHostAddress();
        //如果在同一台机器上启动多个搜索服务，那么必须配置host.local 否则不允许启动
        int count = 0;
        for (Map.Entry<String, String> entry : temps.entrySet()) {
            if (entry.getValue().startsWith(localHostName)) {
                count++;
            }
        }
        if (count > 1 && settings.get("host.local") == null) {
            throw new ConfigurationException("在同一台机器通过端口不同启动多个实例，必须配置host.local");
        }
    }

    private String findLocal() {
        String hostAndPort = settings.get("host.local");
        if (hostAndPort != null) return hostAndPort;
        Map<String, String> temps = settings.getByPrefix("host_alias.").getAsMap();
        String localHostName = NetworkUtils.intranet_ip().getHostAddress();
        for (Map.Entry<String, String> entry : temps.entrySet()) {
            if (entry.getValue().startsWith(localHostName)) {
                return entry.getValue();
            }
        }
        throw new ConfigurationException("无法设置本地机器的ip地址和端口启动服务");
    }

    public Engine.Mapper mapper(String index, String type) {
        return mappingsMap.get(index + "#" + type);
    }

    public Routing routing(String index) {
        return routingMap.get(index);
    }


    public boolean isInMasterSlaveModel() {
        return master != null;
    }


    private List<Host> hosts(Settings settings) {
        String[] hosts = settings.getAsArray("cluster.hosts");

        List<Host> temp = new ArrayList<Host>();
        for (String alasHostName : hosts) {
            temp.add(alias_host.get(alasHostName));
        }
        Collections.sort(temp, new Comparator<Host>() {
            @Override
            public int compare(Host o1, Host o2) {
                return o1.hashCode() - o2.hashCode();
            }
        });

        return temp;
    }

    public List<Host> otherHosts() {
        return otherHosts;
    }

    public static String getPersistFileName() {
        return persistFileName;
    }

    public static void setPersistFileName(String persistFileName) {
        GatewayData.persistFileName = persistFileName;
    }

    public Host getMaster() {
        return master;
    }

    public void setMaster(Host master) {
        this.master = master;
    }

    public Host getLocalHost() {
        return localHost;
    }

    public void setLocalHost(Host localHost) {
        this.localHost = localHost;
    }

    public List<Host> getOtherHosts() {
        return otherHosts;
    }

    public void setOtherHosts(List<Host> otherHosts) {
        this.otherHosts = otherHosts;
    }

    public Map<String, Host> getAlias_host() {
        return alias_host;
    }

    public void setAlias_host(Map<String, Host> alias_host) {
        this.alias_host = alias_host;
    }

    public Map<String, Routing> getRoutingMap() {
        return routingMap;
    }

    public void setRoutingMap(Map<String, Routing> routingMap) {
        this.routingMap = routingMap;
    }

    public Map<String, Engine.Mapper> getMappingsMap() {
        return mappingsMap;
    }

    public void setMappingsMap(Map<String, Engine.Mapper> mappingsMap) {
        this.mappingsMap = mappingsMap;
    }

    public boolean isSingleMode() {
        return singleMode;
    }

    public void setSingleMode(boolean singleMode) {
        this.singleMode = singleMode;
    }
}
