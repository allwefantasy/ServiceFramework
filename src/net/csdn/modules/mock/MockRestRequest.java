package net.csdn.modules.mock;

import net.csdn.common.Booleans;
import net.csdn.common.Unicode;
import net.csdn.common.unit.ByteSizeValue;
import net.csdn.common.unit.TimeValue;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.http.RestUtils;
import org.apache.commons.lang.StringUtils;

import java.util.Map;
import java.util.regex.Pattern;

import static net.csdn.common.unit.ByteSizeValue.parseBytesSizeValue;
import static net.csdn.common.unit.TimeValue.parseTimeValue;

/**
 * User: WilliamZhu
 * Date: 12-7-5
 * Time: 下午5:12
 */
public class MockRestRequest implements RestRequest {
    private static final Pattern commaPattern = Pattern.compile(",");


    private final RestRequest.Method method;

    private final Map<String, String> params;

    private byte[] content;


    public MockRestRequest(Map<String, String> params, RestRequest.Method method, String bodyContentNotForm) {
        this.method = method;
        this.params = params;

        if (bodyContentNotForm != null)
            try {
                content = bodyContentNotForm.getBytes();
                RestUtils.decodeQueryString(bodyContentNotForm, 0, params);
            } catch (Exception e) {
                throw new IllegalArgumentException("Fail to parse request params");
            }
    }

    @Override
    public RestRequest.Method method() {
        return this.method;
    }

    @Override
    public String uri() {
        return null;
    }

    @Override
    public String rawPath() {
        return null;
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
        return null;
    }

    @Override
    public Map<String, String> params() {
        return params;
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
