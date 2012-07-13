package net.csdn.modules.http;

import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.unit.ByteSizeValue;
import net.csdn.common.unit.TimeValue;
import net.csdn.exception.ArgumentErrorException;
import net.csdn.exception.RenderFinish;
import net.csdn.modules.mock.MockRestRequest;
import net.csdn.modules.mock.MockRestResponse;
import net.csdn.reflect.ReflectHelper;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JsonConfig;
import net.sf.json.util.CycleDetectionStrategy;
import net.sf.json.xml.XMLSerializer;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * BlogInfo: william
 * Date: 11-9-6
 * Time: 上午11:12
 */
public abstract class ApplicationController {
    private CSLogger logger = Loggers.getLogger(getClass());
    protected RestRequest request;
    protected RestResponse restResponse;
    public static String EMPTY_JSON = "{}";
    public static String OK = "{\"ok\":true,\"message\":\"{}\"}";
    public static String FAIL = "{\"ok\":false,\"message\":\"{}\"}";

    public void render(int status, String content) {
        restResponse.originContent(content);
        restResponse.write(status, content);
        throw new RenderFinish();
    }


    //默认json
    public void render(int status, Object result) {
        restResponse.originContent(result);
        restResponse.write(status, toJson(result));
        throw new RenderFinish();
    }

    public void render(int status, String content, ViewType viewType) {
        restResponse.originContent(content);
        restResponse.write(status, content, viewType);
        throw new RenderFinish();
    }


    public void render(int status, Object result, ViewType viewType) {
        restResponse.originContent(result);
        restResponse.write(status, viewType == ViewType.xml ? toXML(result) : toJson(result), viewType);
        throw new RenderFinish();
    }


    public void render(String content) {
        restResponse.originContent(content);
        restResponse.write(content);
        throw new RenderFinish();
    }


    public void render(Object result) {
        restResponse.originContent(result);
        restResponse.write(toJson(result));
    }


    public void render(String content, ViewType viewType) {
        restResponse.originContent(content);
        restResponse.write(content, viewType);
        throw new RenderFinish();
    }


    public void render(Object result, ViewType viewType) {
        restResponse.originContent(result);
        restResponse.write(viewType == ViewType.xml ? toXML(result) : toJson(result), viewType);
        throw new RenderFinish();
    }


    public String toJson(Object obj) {
        return _toJson(obj).toString();
    }

    public JSON _toJson(Object obj) {
        JsonConfig config = new JsonConfig();
        config.setIgnoreDefaultExcludes(false);
        config.setCycleDetectionStrategy(CycleDetectionStrategy.NOPROP);
        config.registerJsonValueProcessor(Date.class, new DateJsonValueProcessor());
        if (obj instanceof Collection) {
            return JSONArray.fromObject(obj, config);
        }
        return JSONObject.fromObject(obj, config);
    }

    public String toXML(Object obj) {
        JSON json = _toJson(obj);
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

    public void m(String method) {
        try {
            ReflectHelper.method(this, method);
        } catch (Exception e) {
            if (e instanceof InvocationTargetException) {
                InvocationTargetException invocationTargetException = (InvocationTargetException) e;
                if (invocationTargetException.getTargetException() instanceof RenderFinish) {
                    logger.info("invoke " + method + " done");
                }
            } else if (e instanceof RenderFinish) {

            } else {
                e.printStackTrace();
            }
        }
    }

    public ApplicationController mockRequest(Map<String, String> params, RestRequest.Method method, String xmlOrJsonData) {
        this.request = new MockRestRequest(params, method, xmlOrJsonData);
        this.restResponse = new MockRestResponse();
        return this;
    }

    public RestResponse mockResponse() {
        return restResponse;
    }

}
