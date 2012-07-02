package net.csdn.modules.http;

import net.csdn.common.unit.ByteSizeValue;
import net.csdn.common.unit.TimeValue;
import net.csdn.jpa.model.JPABase;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.util.List;
import java.util.Map;

import static net.csdn.common.logging.support.MessageFormat.format;

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
