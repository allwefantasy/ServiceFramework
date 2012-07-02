package net.csdn.modules.communicate;

import com.google.inject.Inject;
import net.csdn.common.path.Url;
import net.csdn.modules.gateway.GatewayData;
import net.csdn.modules.gateway.GatewayService;
import net.csdn.modules.gateway.Host;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.http.support.HttpStatus;
import net.csdn.modules.transport.HttpTransportService;
import net.sf.json.JSONObject;

import java.util.List;

/**
 * User: WilliamZhu
 * Date: 12-6-7
 * Time: 下午9:55
 */
public class DefaultCommunicateService implements CommunicateService {
    @Inject
    private HttpTransportService transportService;
    @Inject
    private GatewayData gatewayData;
    @Inject
    private GatewayService gatewayService;

    @Override
    public void startUp() {
        List<Url> urls = Url.urls(gatewayData.getOtherHosts(), "/join");
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("host", gatewayData.getLocalHost());
        List<HttpTransportService.SResponse> responses = transportService.asyncHttps(urls, jsonObject.toString(), RestRequest.Method.PUT);

        boolean loadFromLocal = true;
        for (HttpTransportService.SResponse response : responses) {
            if (response.getStatus() == HttpStatus.HttpStatusOK) {
                loadFromLocal = false;
            }
        }

        if (loadFromLocal) {
            gatewayService.load();
            return;
        }
        if (responses.size() != 0) {
            gatewayData.getRoutingMap().clear();
            gatewayData.getMappingsMap().clear();
            gatewayData.getMappingsMapSource().clear();
            gatewayData.getRoutingMapSource().clear();
        }
        for (HttpTransportService.SResponse response : responses) {
            if (response.getStatus() == HttpStatus.HttpStatusOK) {
                JSONObject result = JSONObject.fromObject(response.getContent());

                JSONObject routing = JSONObject.fromObject(result.get("routing"));
                JSONObject mapping = JSONObject.fromObject(result.get("mapping"));
                Host targetHost = (Host) JSONObject.toBean(result.getJSONObject("host"), Host.class);

                for (Host temp_host : gatewayData.getOtherHosts()) {
                    if (temp_host.equals(targetHost)) {
                        temp_host.setOnline(true);
                        if (gatewayData.getMaster() == null && targetHost.isMaster()) {
                            gatewayData.setMaster(temp_host);
                            temp_host.setMaster(true);
                        }
                    }
                }

                for (Object obj : routing.keySet()) {
                    gatewayService.addRouting((String) obj, routing.getString((String) obj));
                }
                for (Object obj : mapping.keySet()) {
                    gatewayService.addMapping((String) obj, mapping.getString((String) obj));
                }
                gatewayService.persist();
            }
        }
        gatewayService.persist();
    }
}
