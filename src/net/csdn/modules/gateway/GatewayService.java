package net.csdn.modules.gateway;

import net.csdn.common.path.Url;
import net.csdn.exception.ArgumentErrorException;
import net.sf.json.JSONObject;

import java.util.List;

/**
 * User: WilliamZhu
 * Date: 12-6-2
 * Time: 上午10:58
 */
public interface GatewayService {
    void persist();

    void load();

    public List<Host> markDownHost(List<Url> urls);

    public GatewayService addRouting(String index, String source);

    public GatewayService removeRouting(String index);

    public GatewayService addMapping(String index, String type, String source) throws ArgumentErrorException;

    public GatewayService addMapping(String key, String source) throws ArgumentErrorException;

    public GatewayService removeMapping(String index, String type);

    public GatewayService removeMapping(String index);

    public JSONObject localInfo();

    public boolean join(Host hostToJoin);
}
