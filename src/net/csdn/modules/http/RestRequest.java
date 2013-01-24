package net.csdn.modules.http;

import net.csdn.common.unit.ByteSizeValue;
import net.csdn.common.unit.TimeValue;

import java.util.Map;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-12
 * Time: 下午10:25
 */
public interface RestRequest {

    enum Method {
        GET, POST, PUT, DELETE, OPTIONS, HEAD
    }

    Method method();

    /**
     * The uri of the rest request, with the query string.
     */
    String uri();

    /**
     * The non decoded, raw path provided.
     */
    String rawPath();

    /**
     * The path part of the URI (without the query string), decoded.
     */
    String path();

    boolean hasContent();

    /**
     * Is the byte array write safe or unsafe for usage on other threads
     */
    boolean contentUnsafe();

    byte[] contentByteArray();

    int contentByteArrayOffset();

    int contentLength();

    String contentAsString();

    String header(String name);

    boolean hasParam(String key);

    String param(String key);

    String param(String key, String defaultValue);

    String paramMultiKey(String... keys);

    String[] paramAsStringArray(String key, String[] defaultValue);

    float paramAsFloat(String key, float defaultValue);

    int paramAsInt(String key, int defaultValue);

    long paramAsLong(String key, long defaultValue);

    boolean paramAsBoolean(String key, boolean defaultValue);

    Boolean paramAsBoolean(String key, Boolean defaultValue);

    TimeValue paramAsTime(String key, TimeValue defaultValue);

    ByteSizeValue paramAsSize(String key, ByteSizeValue defaultValue);

    Map<String, String> params();

    public String cookie(String name);
}
