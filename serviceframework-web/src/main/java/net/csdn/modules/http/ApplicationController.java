package net.csdn.modules.http;

import net.csdn.ServiceFramwork;
import net.csdn.common.Strings;
import net.csdn.common.collect.Tuple;
import net.csdn.common.collections.WowCollections;
import net.csdn.common.exception.ArgumentErrorException;
import net.csdn.common.exception.RenderFinish;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.reflect.ReflectHelper;
import net.csdn.common.settings.Settings;
import net.csdn.common.time.NumberExtendedForTime;
import net.csdn.common.unit.ByteSizeValue;
import net.csdn.common.unit.TimeValue;
import net.csdn.modules.dubbo.DubboServer;
import net.csdn.modules.log.SystemLogger;
import net.csdn.modules.mock.MockRestRequest;
import net.csdn.modules.mock.MockRestResponse;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JsonConfig;
import net.sf.json.util.CycleDetectionStrategy;
import net.sf.json.xml.XMLSerializer;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.joda.time.DateTime;

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Pattern;

import static net.csdn.common.Strings.toUnderscoreCase;

/**
 * BlogInfo: william
 * Date: 11-9-6
 * Time: 上午11:12
 */
public abstract class ApplicationController {
    protected CSLogger logger = Loggers.getLogger(getClass());
    protected RestRequest request;
    protected RestResponse restResponse;
    protected Settings settings = ServiceFramwork.injector.getInstance(Settings.class);
    protected SystemLogger systemLogger = ServiceFramwork.injector.getInstance(SystemLogger.class);

    public Class const_document_get(String name) {
        return inner_const_get("document", name);
    }

    public Class const_service_get(String name) {
        return inner_const_get("service", name);
    }

    private Class inner_const_get(String type, String name) {

        String model = settings.get("application." + type, "") + "." + Strings.toCamelCase(name, true);
        Class clzz = null;
        try {
            clzz = Class.forName(model);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new ArgumentErrorException("error: could not load class:[" + model + "]");
        }
        return clzz;

    }

    protected void merge(Object dest, Object origin) {
        try {
            BeanUtils.copyProperties(dest, origin);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }


    public <T> T findService(Class<T> clzz) {
        return ServiceFramwork.injector.getInstance(clzz);
    }

    public <T> T findRPCService(String name, Class<T> clzz) {
        return findService(DubboServer.class).getBean(name, clzz);
    }

    //session
    public void session(String key, Object value) {
        request.session(key, value);
    }

    //
    public Object session(String key) {
        return request.session(key);
    }

    public String url() {
        return request.url();
    }

    //flash
    public void flash(String key, Object value) {
        request.flash(key, value);
    }

    //
    public Object flash(String key) {
        return request.flash(key);
    }

    //渲染输出
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

    public void redirectTo(String url, Map params) {
        restResponse.redirectTo(url, params);
        throw new RenderFinish();
    }

    public void render(int status, String content, ViewType viewType) {
        restResponse.originContent(content);
        restResponse.write(status, content, viewType);
        throw new RenderFinish();
    }

    public void renderHtml(int status, String path, Map result) {
        VelocityContext context = new VelocityContext();
        copyObjectToMap(result, context);
        StringWriter w = new StringWriter();
        Velocity.mergeTemplate(path, "utf-8", context, w);
        restResponse.write(status, w.toString(), ViewType.html);
    }

    public void renderHtml(int status, String w) {
        restResponse.write(status, w, ViewType.html);
    }

    public void renderHtmlWithMaster(int status, String path, Map result) {
        if (!result.containsKey("template")) {
            result.put("template", toUnderscoreCase(getControllerNameWithoutSuffix()) + "/" + toUnderscoreCase(getActionName()) + ".vm");
        }
        renderHtml(status, path, result);
    }


    public void render(int status, Object result, ViewType viewType) {
        restResponse.originContent(result);
        if (viewType == ViewType.xml) {
            restResponse.write(status, toXML(result), viewType);
        } else if (viewType == ViewType.json) {
            restResponse.write(status, toJson(result), viewType);
        } else if (viewType == ViewType.string) {
            restResponse.write(status, result.toString(), viewType);
        } else if (viewType == ViewType.html) {

            VelocityContext context = new VelocityContext();

            copyObjectToMap(result, context);

            StringWriter w = new StringWriter();
            Velocity.mergeTemplate(toUnderscoreCase(getControllerNameWithoutSuffix()) + "/" + toUnderscoreCase(getActionName()) + ".vm", "utf-8", context, w);
            restResponse.write(status, w.toString(), viewType);
        }
        throw new RenderFinish();
    }

    private void copyObjectToMap(Object result, VelocityContext context) {
        if (result instanceof Map) {
            Map<String, Object> temp = (Map) result;
            for (Map.Entry<String, Object> entry : temp.entrySet()) {
                context.put(entry.getKey(), entry.getValue());
            }
        }
        context.put("helper", findHelper());
        //put all instance variables in context
        for (Field field : this.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            try {
                context.put(field.getName(), field.get(this));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private String getControllerNameWithoutSuffix() {
        return StringUtils.substringBefore(getControllerName(), "Controller");
    }

    private String getControllerName() {
        return getClass().getSimpleName();
    }

    private String getActionName() {
        RestController restController = ServiceFramwork.injector.getInstance(RestController.class);
        Tuple<Class<ApplicationController>, Method> handlerKey = restController.getHandler(request);
        return handlerKey.v2().getName();
    }

    private Object findHelper() {
        String wow = StringUtils.substringAfter(
                StringUtils.substringBefore(getClass().getName(), "." + getClass().getSimpleName()),
                settings.get("application.controller", "") + ".");
        if (isEmpty(wow)) {
            wow = (settings.get("application.helper", "") + "." + getControllerNameWithoutSuffix() + "Helper");
        } else {
            wow = (settings.get("application.helper", "") + "." + wow + "." + getControllerNameWithoutSuffix() + "Helper");
        }

        Object instance;
        try {
            instance = Class.forName(wow).newInstance();
        } catch (Exception e) {
            instance = new WowCollections();
        }
        return instance;
    }


    public void render(String content) {
        restResponse.originContent(content);
        restResponse.write(content);
        throw new RenderFinish();
    }


    public void render(Object result) {
        restResponse.originContent(result);
        restResponse.write(toJson(result));
        throw new RenderFinish();
    }


    public class JSONOutPutConfig extends JsonConfig {
        private boolean pretty = false;

        public boolean isPretty() {
            return pretty;
        }

        public void setPretty(boolean pretty) {
            this.pretty = pretty;
        }
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
        if (config.isPretty()) {
            return _toJson(obj).toString(2);
        }
        return _toJson(obj).toString();
    }

    protected JSONOutPutConfig config = new JSONOutPutConfig();

    public JSON _toJson(Object obj) {
        JsonConfig config = new JsonConfig();
        config.setIgnoreDefaultExcludes(false);
        config.setCycleDetectionStrategy(CycleDetectionStrategy.LENIENT);
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


    public JSONObject paramAsJSON(String key) {
        return JSONObject.fromObject(param(key));
    }

    public JSONArray paramAsJSONArray(String key) {
        return JSONArray.fromObject(param(key));
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

    //获取http参数
    public String header(String name) {
        return request.header(name);
    }

    public String cookie(String name) {
        return request.cookie(name);
    }

    public void cookie(String name, String value) {
        restResponse.cookie(name, value);
    }

    public void cookie(Map cookieInfo) {
        restResponse.cookie(cookieInfo);
    }

    public void cookie(String name, String value, String path, int max_age) {
        restResponse.cookie(map(
                "name", name,
                "value", value,
                "path", path,
                "max_age", max_age
        ));
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


    public String paramMultiKey(String... keys) {
        return request.paramMultiKey(keys);
    }

    public String param(String key, String defaultValue) {
        return request.param(key, defaultValue);
    }

    public boolean isEmpty(String abc) {
        return StringUtils.isEmpty(abc);
    }

    public boolean isEmpty(Collection abc) {
        return WowCollections.isEmpty(abc);
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

    //调用方法
    public void m(String method) {
        try {
            ReflectHelper.method2(this, method);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    //单元测试可以调用
    public ApplicationController mockRequest(Map<String, String> params, RestRequest.Method method, String xmlOrJsonData) {
        this.request = new MockRestRequest(params, method, xmlOrJsonData);
        this.restResponse = new MockRestResponse();
        return this;
    }

    public RestResponse mockResponse() {
        return restResponse;
    }

    //各种工具方法
    public <T> Set<T> newHashSet(T... arrays) {
        return WowCollections.newHashSet(arrays);
    }


    //@see  selectMap
    public Map selectMap(Map map, String... keys) {
        return WowCollections.selectMap(map, keys);
    }

    public Map paramByKeys(Map map, String... keys) {
        return WowCollections.selectMap(map, keys);
    }

    //@see aliasParamKeys
    public Map selectMapWithAliasName(Map map, String... keys) {
        return WowCollections.selectMapWithAliasName(map, keys);
    }

    public Map aliasParamKeys(Map params, String... keys) {
        return WowCollections.selectMapWithAliasName(params, keys);
    }

    public Map map(Object... arrays) {
        return WowCollections.map(arrays);
    }


    public <T> List<T> list(T... arrays) {
        return WowCollections.list(arrays);
    }

    //@see project
    public <T> List<T> projectionColumn(List<Map> maps, String column) {
        return WowCollections.projectionColumn(maps, column);
    }

    public List project(List<Map> list, String key) {
        return WowCollections.project(list, key);
    }

    public String join(Collection collection, String split) {
        return WowCollections.join(collection, split);
    }

    public String join(Collection collection) {
        return WowCollections.join(collection);
    }

    public List projectByMethod(List list, String method, Object... params) {
        return WowCollections.projectByMethod(list, method, params);
    }

    public Map double_list_to_map(List keys, List values) {
        return WowCollections.doubleListToMap(keys, values);
    }

    public String join(Collection collection, String split, String wrapper) {
        return WowCollections.join(collection, split, wrapper);
    }

    public String join(Object[] collection, String split, String wrapper) {
        return WowCollections.join(collection, split, wrapper);
    }

    public String getString(Map map, String key) {
        return WowCollections.getString(map, key);
    }

    public String getStringNoNull(Map map, String key) {
        return WowCollections.getStringNoNull(map, key);
    }

    public Date getDate(Map map, String key) {
        return WowCollections.getDate(map, key);
    }

    public long getDateAsLong(Map map, String key) {
        return WowCollections.getDateAsLong(map, key);
    }

    public int getInt(Map map, String key) {
        return WowCollections.getInt(map, key);
    }

    public long getLong(Map map, String key) {
        return WowCollections.getLong(map, key);
    }

    public Set hashSet(Object[] array) {
        return WowCollections.hashSet(array);
    }


    public List toList(Object[] array) {
        return WowCollections.toList(array);
    }


    public Set hashSet(int[] array) {
        return WowCollections.hashSet(array);
    }

    public List jsonArrayToList(JSONArray jsonArray) {
        return toList(jsonArray.toArray());

    }

    public String join(Object[] arrays, String split) {
        return WowCollections.join(arrays, split);
    }

    public String join(int[] arrays, String split) {

        return WowCollections.join(arrays, split);
    }

    public <T> T or(T a, T b) {
        if (a == null) {
            return b;
        }
        return a;
    }

    public Map selectMapWithAliasNameInclude(Map map, String... keys) {
        return WowCollections.selectMapWithAliasNameInclude(map, keys);
    }

    public Pattern RegEx(String reg) {
        return Pattern.compile(reg);
    }

    public Pattern regEx(String reg) {
        return RegEx(reg);
    }

    public Pattern paramsAsRegEx(String key) {
        return RegEx(param(key));
    }

    public boolean isNull(Object key) {
        return key == null;
    }

    //时间扩展
    public NumberExtendedForTime time(int number) {
        return new NumberExtendedForTime(number);
    }

    //时间扩展
    public NumberExtendedForTime time(long number) {
        return new NumberExtendedForTime(number);
    }

    public DateTime now() {
        return new DateTime();
    }

    public static Map<String, Map<String, List>> parent$_before_filter_info;
    public static Map<String, Map<String, List>> parent$_around_filter_info;

    public static Map<String, Map<String, List>> parent$_before_filter_info() {
        if (parent$_before_filter_info == null) {
            parent$_before_filter_info = new LinkedHashMap();
        }
        return parent$_before_filter_info;
    }

    public static Map<String, Map<String, List>> parent$_around_filter_info() {
        if (parent$_around_filter_info == null) {
            parent$_around_filter_info = new LinkedHashMap();
        }
        return parent$_around_filter_info;
    }

    public static void beforeFilter(String filter, Map info) {
        parent$_before_filter_info().put(filter, info);
    }

    public static void aroundFilter(String filter, Map info) {
        parent$_around_filter_info().put(filter, info);
    }

}
