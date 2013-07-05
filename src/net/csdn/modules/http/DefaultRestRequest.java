package net.csdn.modules.http;

import net.csdn.common.Booleans;
import net.csdn.common.Unicode;
import net.csdn.common.io.Streams;
import net.csdn.common.unit.ByteSizeValue;
import net.csdn.common.unit.TimeValue;
import net.sf.json.util.JSONUtils;
import org.apache.commons.lang.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static net.csdn.common.unit.ByteSizeValue.parseBytesSizeValue;
import static net.csdn.common.unit.TimeValue.parseTimeValue;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-12
 * Time: 下午10:27
 */
public class DefaultRestRequest implements RestRequest {
    private static final Pattern commaPattern = Pattern.compile(",");

    private final HttpServletRequest servletRequest;

    private Method method;

    private final Map<String, String> params;

    private final byte[] content;

    public DefaultRestRequest(String method, Map params) {

        this.method = Method.valueOf(method);
        this.params = params;
        servletRequest = null;
        content = new byte[0];

    }

    public DefaultRestRequest(HttpServletRequest servletRequest) {
        this.servletRequest = servletRequest;
        this.method = Method.valueOf(servletRequest.getMethod());
        this.params = new HashMap<String, String>();

        if (servletRequest.getQueryString() != null) {
            RestUtils.decodeQueryString(servletRequest.getQueryString(), 0, params);
        }
        if (params.containsKey("_method")) {
            this.method = Method.valueOf(params.get("_method"));
        }
        //application/x-www-form-urlencoded
        String contentType = servletRequest.getHeader("content-type");
        try {
            content = Streams.copyToByteArray(servletRequest.getInputStream());
        } catch (IOException e) {
            throw new IllegalArgumentException("Fail to parse request params");
        }
        if ("application/json".equals(contentType)) return;

        String wow = contentAsString();
        if (wow == null) return;
        wow = wow.trim();
        if (JSONUtils.mayBeJSON(wow)) {
            //我们猜测是json数据什么都不做
            return;
        }
        if ("application/x-www-form-urlencoded".equals(contentType))
            RestUtils.decodeQueryString(wow, 0, params);

        if (params.containsKey("_method")) {
            this.method = Method.valueOf(params.get("_method"));
        }
    }

    @Override
    public Method method() {
        return this.method;
    }

    @Override
    public String uri() {
        return servletRequest.getRequestURI();
    }

    @Override
    public String rawPath() {
        return servletRequest.getRequestURI();
    }

    @Override
    public boolean hasContent() {
        return content.length > 0;
    }

    @Override
    public boolean contentUnsafe() {
        return false;
    }

    @Override
    public byte[] contentByteArray() {
        return content;
    }

    @Override
    public int contentByteArrayOffset() {
        return 0;
    }

    @Override
    public int contentLength() {
        return content.length;
    }

    @Override
    public String contentAsString() {
        return Unicode.fromBytes(contentByteArray(), contentByteArrayOffset(), contentLength());
    }

    @Override
    public String header(String name) {
        return servletRequest.getHeader(name);
    }

    @Override
    public Map<String, String> params() {
        return params;
    }

    @Override
    public String cookie(String name) {
        Cookie[] cookies = servletRequest.getCookies();
        if (cookies == null || cookies.length == 0) return null;
        for (Cookie cookie : cookies) {
            if (cookie.getName().equalsIgnoreCase(name)) {
                return cookie.getValue();
            }
        }
        return null;
    }

    @Override
    public Object session(String key) {
        return servletRequest.getSession().getAttribute(key);
    }

    @Override
    public void session(String key, Object value) {
        servletRequest.getSession().setAttribute(key, value);
    }


    @Override
    public Object flash(String key) {
        return servletRequest.getAttribute(key);
    }

    @Override
    public void flash(String key, Object value) {
        servletRequest.setAttribute(key, value);
    }

    @Override
    public boolean hasParam(String key) {
        return params.containsKey(key);
    }

    @Override
    public String param(String key) {
        return params.get(key);
    }

    @Override
    public String paramMultiKey(String... keys) {
        for (String key : keys) {
            String temp = param(key);
            if (!StringUtils.isEmpty(temp))
                return temp;
        }
        return null;
    }

    public String param(String key, String defaultValue) {
        String value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }


    @Override
    public final String path() {
        return RestUtils.decodeComponent(rawPath());
    }

    @Override
    public float paramAsFloat(String key, float defaultValue) {
        String sValue = param(key);
        if (sValue == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(sValue);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Failed to parse float parameter [" + key + "] with value [" + sValue + "]", e);
        }
    }

    @Override
    public int paramAsInt(String key, int defaultValue) {
        String sValue = param(key);
        if (sValue == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(sValue);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Failed to parse int parameter [" + key + "] with value [" + sValue + "]", e);
        }
    }

    @Override
    public long paramAsLong(String key, long defaultValue) {
        String sValue = param(key);
        if (sValue == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(sValue);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Failed to parse int parameter [" + key + "] with value [" + sValue + "]", e);
        }
    }

    @Override
    public boolean paramAsBoolean(String key, boolean defaultValue) {
        return Booleans.parseBoolean(param(key), defaultValue);
    }

    @Override
    public Boolean paramAsBoolean(String key, Boolean defaultValue) {
        String sValue = param(key);
        if (sValue == null) {
            return defaultValue;
        }
        return !(sValue.equals("false") || sValue.equals("0") || sValue.equals("off"));
    }

    @Override
    public TimeValue paramAsTime(String key, TimeValue defaultValue) {
        return parseTimeValue(param(key), defaultValue);
    }

    @Override
    public ByteSizeValue paramAsSize(String key, ByteSizeValue defaultValue) {
        return parseBytesSizeValue(param(key), defaultValue);
    }

    @Override
    public String[] paramAsStringArray(String key, String[] defaultValue) {
        String value = param(key);
        if (value == null) {
            return defaultValue;
        }
        return commaPattern.split(value);
    }
}
