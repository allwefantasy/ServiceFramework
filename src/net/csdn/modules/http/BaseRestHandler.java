package net.csdn.modules.http;

import com.google.inject.Inject;
import net.csdn.CsdnSearchIllegalArgumentException;
import net.csdn.cluster.routing.Routing;
import net.csdn.cluster.routing.Shard;
import net.csdn.common.Booleans;
import net.csdn.common.Strings;
import net.csdn.common.Unicode;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.path.Url;
import net.csdn.common.settings.Settings;
import net.csdn.common.unit.ByteSizeValue;
import net.csdn.common.unit.TimeValue;
import net.csdn.env.Environment;
import net.csdn.exception.RecordNotFoundException;
import net.csdn.jpa.model.JPABase;
import net.csdn.modules.analyzer.AnalyzerService;
import net.csdn.modules.gateway.GatewayService;
import net.csdn.modules.http.support.HttpStatus;
import net.csdn.modules.index.IndexService;
import net.csdn.modules.gateway.GatewayData;
import net.csdn.modules.persist.PersistService;
import net.csdn.modules.persist.mongodb.MongoClient;
import net.csdn.modules.search.SearchService;
import net.csdn.modules.threadpool.ThreadPoolService;
import net.csdn.modules.transport.HttpTransportService;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.lucene.index.Engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static net.csdn.common.logging.support.MessageFormat.format;
import static net.csdn.common.unit.ByteSizeValue.parseBytesSizeValue;
import static net.csdn.common.unit.TimeValue.parseTimeValue;


/**
 * User: william
 * Date: 11-9-6
 * Time: 上午11:12
 */
public abstract class BaseRestHandler {
    protected RestRequest request;
    protected RestResponse restResponse;
    public static String EMPTY_JSON = "{}";
    public static String OK = "{\"ok\":true,\"message\":\"{}\"}";

    public void render(int status, String content) {
        restResponse.write(status, content);
    }

    public void render(JPABase model) {
        restResponse.write(JSONObject.fromObject(model).toString());
    }

    public void render(List result) {
        restResponse.write(JSONArray.fromObject(result).toString());
    }

    public void render(String content) {
        restResponse.write(content);
    }

    public void render(String jsonFormat, String message) {
        restResponse.write(format(jsonFormat, message));
    }


    public String contentAsString() {
        return request.contentAsString();
    }

    public JSONObject contentAsJSON() {
        return JSONObject.fromObject(contentAsString());
    }

    public String header(String name) {
        return request.header(name);
    }

    public Map<String, String> params() {
        return request.params();
    }

    public boolean hasParam(String key) {
        return request.params().containsKey(key);
    }

    public String param(String key) {
        return request.params().get(key);
    }

    public String param(String... keys) {
        return request.param(keys);
    }

    public String param(String key, String defaultValue) {
        return request.param(key, defaultValue);
    }


    public float paramAsFloat(String key, float defaultValue) {
        return request.paramAsFloat(key, defaultValue);
    }

    public int paramAsInt(String key, int defaultValue) {
        return request.paramAsInt(key, defaultValue);
    }

    public long paramAsLong(String key, long defaultValue) {
        return request.paramAsLong(key, defaultValue);
    }

    public boolean paramAsBoolean(String key, boolean defaultValue) {
        return request.paramAsBoolean(key, defaultValue);
    }

    public Boolean paramAsBoolean(String key, Boolean defaultValue) {
        return request.paramAsBoolean(key, defaultValue);
    }

    public TimeValue paramAsTime(String key, TimeValue defaultValue) {
        return request.paramAsTime(key, defaultValue);
    }

    public ByteSizeValue paramAsSize(String key, ByteSizeValue defaultValue) {
        return request.paramAsSize(key, defaultValue);

    }

    public String[] paramAsStringArray(String key, String[] defaultValue) {
        return request.paramAsStringArray(key, defaultValue);
    }

}
