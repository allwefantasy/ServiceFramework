package net.csdn.modules.http;

import net.csdn.common.unit.ByteSizeValue;
import net.csdn.common.unit.TimeValue;
import net.csdn.exception.ArgumentErrorException;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.xml.XMLSerializer;

import java.util.Collection;
import java.util.Map;

/**
 * User: william
 * Date: 11-9-6
 * Time: 上午11:12
 */
public abstract class ApplicationController {
    protected RestRequest request;
    protected RestResponse restResponse;
    public static String EMPTY_JSON = "{}";
    public static String OK = "{\"ok\":true,\"message\":\"{}\"}";

    public void render(int status, String content) {
        restResponse.write(status, content);
    }


    //默认json
    public void render(int status, Object result) {
        restResponse.write(status, toJson(result));
    }

    public void render(int status, String content, ViewType viewType) {
        restResponse.write(status, content, viewType);
    }


    public void render(int status, Object result, ViewType viewType) {
        restResponse.write(status, viewType == ViewType.xml ? toXML(result) : toJson(result), viewType);
    }


    public void render(String content) {
        restResponse.write(content);
    }


    public void render(Object result) {
        restResponse.write(toJson(result));
    }


    public void render(String content, ViewType viewType) {
        restResponse.write(content, viewType);
    }


    public void render(Object result, ViewType viewType) {
        restResponse.write(viewType == ViewType.xml ? toXML(result) : toJson(result), viewType);
    }


    public String toJson(Object obj) {

        if (obj instanceof Collection) {
            return JSONArray.fromObject(obj).toString();
        }
        return JSONObject.fromObject(obj).toString();
    }

    public String toXML(Object obj) {
        JSON json = obj instanceof Collection ? JSONArray.fromObject(obj) : JSONObject.fromObject(obj);
        XMLSerializer xmlSerializer = new XMLSerializer();
        return xmlSerializer.write(json);
    }


    public String contentAsString() {
        return request.contentAsString();
    }


    public JSONObject paramAsJSON() {
        JSON json = _contentAsJSON();
        if (json.isArray()) {
            throw new ArgumentErrorException("数据格式错误，您需要传入json格式");
        }
        return (JSONObject) json;
    }

    public JSONArray paramsAsJSONArray() {
        JSON json = _contentAsJSON();
        if (!json.isArray()) throw new ArgumentErrorException("数据格式错误，您需要传入json格式");
        return (JSONArray) json;
    }

    private JSON _contentAsJSON() {
        try {
            return JSONObject.fromObject(contentAsString());
        } catch (Exception e) {
            try {
                return JSONArray.fromObject(contentAsString());
            } catch (Exception e1) {
                throw new ArgumentErrorException("数据格式错误，您需要传入json格式");
            }

        }

    }

    public JSONArray paramAsXMLArray() {
        JSON json = _contentAsXML();
        if (!json.isArray()) throw new ArgumentErrorException("数据格式错误，您需要传入json格式");
        return (JSONArray) json;
    }

    public JSONObject paramAsXML() {
        JSON json = _contentAsXML();
        if (json.isArray()) {
            throw new ArgumentErrorException("数据格式错误，您需要传入json格式");
        }
        return (JSONObject) json;
    }

    private JSON _contentAsXML() {
        try {
            XMLSerializer xmlSerializer = new XMLSerializer();
            JSON json = xmlSerializer.read(contentAsString());
            return json;
        } catch (Exception e) {
            throw new ArgumentErrorException("数据格式错误，您需要传入json格式");
        }
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

    public int paramAsInt(String key) {
        if (param(key) == null) throw new ArgumentErrorException("");
        return request.paramAsInt(key, -1);
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
