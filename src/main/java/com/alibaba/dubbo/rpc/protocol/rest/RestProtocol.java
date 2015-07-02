package com.alibaba.dubbo.rpc.protocol.rest;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.protocol.AbstractProxyProtocol;
import com.google.common.collect.Sets;
import net.csdn.ServiceFramwork;
import net.csdn.annotation.rest.At;
import net.csdn.common.collections.WowCollections;
import net.csdn.common.path.Url;
import net.csdn.common.settings.Settings;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.transport.HttpTransportService;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URLEncoder;
import java.util.*;

/**
 * 7/2/15 WilliamZhu(allwefantasy@gmail.com)
 */
public class RestProtocol extends AbstractProxyProtocol {
    private final static Settings settings = ServiceFramwork.injector.getInstance(Settings.class);
    private final static HttpTransportService transportService = ServiceFramwork.injector.getInstance(HttpTransportService.class);
    public static final String NAME = "rest";

    @Override
    protected <T> Runnable doExport(T t, Class<T> tClass, URL url) throws RpcException {
        return new Runnable() {
            @Override
            public void run() {
                logger.info("不允许关闭");
            }
        };
    }

    @Override
    protected <T> T doRefer(Class<T> tClass, URL url) throws RpcException {

        RestClientProxy restClientProxy = new RestClientProxy(transportService);
        restClientProxy.target("http://" + url.getHost() + ":" + url.getPort() + "/" + getContextPath(url));
        Class<?>[] intfs =
                {
                        tClass
                };
        return (T) Proxy.newProxyInstance(RestProtocol.class.getClassLoader(), intfs, restClientProxy);
    }

    protected String getContextPath(URL url) {
        int pos = url.getPath().lastIndexOf("/");
        return pos > 0 ? url.getPath().substring(0, pos) : "";
    }

    @Override
    public int getDefaultPort() {
        return settings.getAsInt("http.port", 9002);
    }

    class RestClientProxy implements InvocationHandler {

        private String url;
        private HttpTransportService httpTransportService;

        RestClientProxy(HttpTransportService httpTransportService) {
            this.httpTransportService = httpTransportService;
        }

        public void target(String url) {
            this.url = url;
        }

        public final String RestMethod = "__rest_method__";
        public final String NotFormEncodeParams = "__content_type__";
        public final Set<String> filters = Sets.newHashSet(RestMethod);

        private String encodeParams(Map<String, String> params) {

            List<String> keywords = new ArrayList<String>();
            for (Map.Entry<String, String> en : params.entrySet()) {
                if (filters.contains(en.getKey())) continue;
                try {
                    keywords.add(en.getKey() + "=" + URLEncoder.encode(en.getValue(), "utf-8"));
                } catch (UnsupportedEncodingException e) {
                }
            }
            return WowCollections.join(keywords, "&");
        }

        private Map<String, String> filterParams(Map<String, String> params) {

            Map<String, String> temp = new HashMap<String, String>();
            for (Map.Entry<String, String> en : params.entrySet()) {
                if (filters.contains(en.getKey())) continue;
                temp.put(en.getKey(), en.getValue());
            }
            return temp;
        }

        @Override
        public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
            Map<String, String> obj = (Map<String, String>) objects[0];
            String reqMethodStr = obj.get(RestMethod);
            if (reqMethodStr == null) reqMethodStr = "GET";
            RestRequest.Method reqMethod = RestRequest.Method.valueOf(reqMethodStr);
            boolean notFormEncodeParams = obj.get(NotFormEncodeParams) != null;
            At at = method.getAnnotation(At.class);
            String path = at.path()[0];
            RestRequest.Method[] httpMethods = at.types();
            boolean methodSupport = false;
            for (RestRequest.Method mt : httpMethods) {
                if (mt.equals(reqMethod)) {
                    methodSupport = true;
                }
            }
            if (!methodSupport) throw new RuntimeException(reqMethod + "not support in invoke " + url);
            HttpTransportService.SResponse response = null;
            Url finalUrl = new Url(url + "/" + path);
            if (reqMethod.equals(RestRequest.Method.GET)) {
                response = httpTransportService.get(finalUrl, filterParams(obj));
            } else if (reqMethod.equals(RestRequest.Method.POST)) {
                if (notFormEncodeParams) {
                    response = httpTransportService.http(finalUrl, obj.get(NotFormEncodeParams), RestRequest.Method.POST);
                } else {
                    response = httpTransportService.post(finalUrl, filterParams(obj));
                }
            } else if (reqMethod.equals(RestRequest.Method.PUT)) {
                if (notFormEncodeParams) {
                    response = httpTransportService.http(finalUrl, obj.get(NotFormEncodeParams), RestRequest.Method.PUT);
                } else {
                    response = httpTransportService.put(finalUrl, filterParams(obj));
                }
            } else if (reqMethod.equals(RestRequest.Method.DELETE)) {
                if (notFormEncodeParams) {
                    response = httpTransportService.http(finalUrl, obj.get(NotFormEncodeParams), RestRequest.Method.DELETE);
                } else {
                    Map<String, String> abc = filterParams(obj);
                    abc.put("_method", "DELETE");
                    response = httpTransportService.post(finalUrl, abc);
                }
            }
            if (response == null || response.getStatus() != 200) return null;
            return response.getContent();
        }
    }
}

