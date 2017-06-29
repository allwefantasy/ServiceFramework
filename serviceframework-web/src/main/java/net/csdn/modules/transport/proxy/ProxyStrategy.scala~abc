package net.csdn.modules.transport.proxy

import java.lang.reflect.Method

import com.alibaba.dubbo.rpc.protocol.rest.RestClientProxy
import net.csdn.modules.transport.HttpTransportService.SResponse

/**
 * 10/14/15 WilliamZhu(allwefantasy@gmail.com)
 */
trait ProxyStrategy {
  def invoke(proxyList: java.util.List[RestClientProxy], o: Object, method: Method, objects: Array[Object]): java.util.List[SResponse]
}
