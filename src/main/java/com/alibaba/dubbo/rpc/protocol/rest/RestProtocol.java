package com.alibaba.dubbo.rpc.protocol.rest;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.protocol.AbstractProxyProtocol;
import net.csdn.ServiceFramwork;
import net.csdn.annotation.Param;
import net.csdn.annotation.rest.At;
import net.csdn.common.collections.WowCollections;
import net.csdn.common.path.Url;
import net.csdn.common.settings.Settings;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.transport.HttpTransportService;

import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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


        private String encodeParams(Map<String, String> params) {

            List<String> keywords = new ArrayList<String>();
            for (Map.Entry<String, String> en : params.entrySet()) {
                try {
                    keywords.add(en.getKey() + "=" + URLEncoder.encode(en.getValue(), "utf-8"));
                } catch (UnsupportedEncodingException e) {
                }
            }
            return WowCollections.join(keywords, "&");
        }


        @Override
        public Object invoke(Object o, Method method, Object[] objects) throws Throwable {

//            Annotation[][] annotations = method.getParameterAnnotations();
//            for (Annotation[] ann : annotations) {
//                System.out.printf("%d annotatations", ann.length);
//                System.out.println();
//            }

            At at = method.getAnnotation(At.class);

            String path = at.path()[0];
            RestRequest.Method[] httpMethods = at.types();

            Map<String, String> params = new HashMap<String, String>();
            RestRequest.Method reqMethod = httpMethods[0];
            String body = null;
            Annotation[][] annotations = method.getParameterAnnotations();

            for (int i = 0; i < objects.length; i++) {
                Object abc = objects[i];
                if (annotations[i].length > 0) continue;
                if (abc instanceof Map) {
                    params.putAll((Map) abc);
                } else if (abc instanceof RestRequest.Method) {
                    reqMethod = (RestRequest.Method) abc;
                } else if (abc instanceof String) {
                    body = (String) abc;
                }
            }


            for (int i = 0; i < annotations.length; i++) {
                Annotation[] paramAnnoes = annotations[i];
                for (Annotation paramAnno : paramAnnoes) {
                    if (paramAnno instanceof Param) {
                        Param temp = (Param) paramAnno;
                        params.put(temp.value(), (String) objects[i]);
                    }
                }
            }


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
                response = httpTransportService.get(finalUrl, params);
            } else {
                if (body == null) {
                    response = httpTransportService.post(finalUrl, params);
                } else {
                    finalUrl.query(encodeParams(params));
                    response = httpTransportService.http(finalUrl, body, reqMethod);
                }

            }

            if (response == null)
                return new HttpTransportService.SResponse(-1, "network fail or timeout", finalUrl);
            return response;
        }
    }
}

