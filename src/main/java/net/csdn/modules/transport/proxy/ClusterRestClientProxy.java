package net.csdn.modules.transport.proxy;

import com.alibaba.dubbo.rpc.protocol.rest.RestClientProxy;
import net.csdn.modules.transport.HttpTransportService;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 10/14/15 WilliamZhu(allwefantasy@gmail.com)
 */
public class ClusterRestClientProxy implements InvocationHandler {

    private List<RestClientProxy> proxyList;
    private ProxyStrategy proxyStrategy;

    public ClusterRestClientProxy(List<RestClientProxy> proxyList, ProxyStrategy proxyStrategy) {
        this.proxyList = proxyList;
        this.proxyStrategy = proxyStrategy;
    }

    @Override
    public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
        if (proxyStrategy == null) {
            proxyStrategy = new FirstMeetProxyStrategy();
        }
        List<HttpTransportService.SResponse> responses = proxyStrategy.invoke(proxyList, o, method, objects);
        return responses;
    }
}
