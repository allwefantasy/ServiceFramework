package com.alibaba.dubbo.rpc.protocol.rest;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.protocol.AbstractProxyProtocol;
import net.csdn.ServiceFramwork;
import net.csdn.common.settings.Settings;
import net.csdn.modules.transport.HttpTransportService;

import java.lang.reflect.Proxy;

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
}

