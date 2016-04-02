package com.alibaba.dubbo.rpc.protocol.rest;

import net.csdn.annotation.Param;
import net.csdn.annotation.rest.At;
import net.csdn.common.collections.WowCollections;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.path.Url;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.transport.HttpTransportService;
import net.csdn.trace.RemoteTraceElementKey;
import net.csdn.trace.Trace;
import net.csdn.trace.TraceContext;
import net.csdn.trace.VisitType;
import org.apache.commons.lang3.exception.ExceptionUtils;
import scala.collection.JavaConversions;

import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 10/14/15 WilliamZhu(allwefantasy@gmail.com)
 */
public class RestClientProxy implements InvocationHandler {
    private CSLogger logger = Loggers.getLogger(RestClientProxy.class);
    private String url;
    private HttpTransportService httpTransportService;

    public RestClientProxy(HttpTransportService httpTransportService) {
        this.httpTransportService = httpTransportService;
    }

    public void target(String url) {
        this.url = url;
    }

    public String hostAndPort() {
        return new Url(url).hostAndPort();
    }

    public String url() {
        return url;
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
        //Trace
        At at = method.getAnnotation(At.class);
        if (at == null) return method.invoke(o, objects);

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
            } else if (abc instanceof scala.collection.immutable.Map) {
                scala.collection.immutable.Map<String, String> kk = (scala.collection.immutable.Map<String, String>) abc;
                Map<String, String> newKK = JavaConversions.mapAsJavaMap(kk);
                params.putAll(newKK);
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






        //支持rest接口
        List<String> replaceKeys = new ArrayList<String>();
        String remenberPath = path;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String newPath = path.replace("{" + entry.getKey() + "}", entry.getValue());
            if (!newPath.equals(remenberPath)) {
                remenberPath = newPath;
                replaceKeys.add(entry.getKey());
            }
        }
        for (String removeKey : replaceKeys) {
            params.remove(removeKey);
        }
        Url finalUrl = new Url(url + "/" + remenberPath);

        TraceContext traceContext = Trace.get();
        if (traceContext != null) {
            traceContext.start(finalUrl.toString(), VisitType.HTTP_SERVICE());
            finalUrl.addParam(RemoteTraceElementKey.TRACEID(), traceContext.traceId());
            finalUrl.addParam(RemoteTraceElementKey.RPCID(), traceContext.currentRpcId());
        }

        HttpTransportService.SResponse response = null;
        String message = null;
        try {
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
        } catch (Exception e) {
            message = ExceptionUtils.getStackTrace(e).replaceAll("\n", "\\n");
        } finally {
            int responseStatus = -1;
            long responseLength = -1;
            if (response != null) responseStatus = response.getStatus();
            if (response != null) responseLength = response.getContent().length();
            if (traceContext != null) {
                traceContext.finish(responseStatus, responseLength, message, logger);
            }
        }

        if (response == null)
            return new HttpTransportService.SResponse(-1, "network fail or timeout", finalUrl);
        return response;
    }
}
