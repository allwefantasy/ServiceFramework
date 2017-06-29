package net.csdn.modules.transport.proxy

import java.lang.reflect.Method
import java.util

import com.alibaba.dubbo.rpc.protocol.rest.RestClientProxy
import net.csdn.modules.transport.HttpTransportService.SResponse

/**
 * 10/14/15 WilliamZhu(allwefantasy@gmail.com)
 */
class FirstMeetProxyStrategy extends ProxyStrategy {
  override def invoke(proxyList: util.List[RestClientProxy], o: Object, method: Method, objects: Array[Object]): util.List[SResponse] = {
    val responses = new util.ArrayList[SResponse]()
    if (proxyList == null || proxyList.size() == 0) return responses
    responses.add(proxyList.get(0).invoke(o, method, objects).asInstanceOf[SResponse])
    responses
  }
}
