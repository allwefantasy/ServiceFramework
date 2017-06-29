package net.csdn.modules.transport.proxy

import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap

import com.alibaba.dubbo.rpc.protocol.rest.RestClientProxy
import net.csdn.ServiceFramwork
import net.csdn.common.settings.{ImmutableSettings, Settings}
import net.csdn.modules.threadpool.DefaultThreadPoolService
import net.csdn.modules.transport.{DefaultHttpTransportService, HttpTransportService}

/**
 * 10/14/15 WilliamZhu(allwefantasy@gmail.com)
 */

object AggregateRestClient {

  private final val clients = new ConcurrentHashMap[String, Any]()

  def buildTransportService = {
    val settings: Settings = ImmutableSettings.settingsBuilder().loadFromClasspath("application.yml").build()
    new DefaultHttpTransportService(new DefaultThreadPoolService(settings), settings)
  }

  def buildIfPresent[T](hostAndPort: String, f: (ProxyStrategy, HttpTransportService) => T)(implicit manifest: Manifest[T]) = {
    if (clients.containsKey(hostAndPort)) {
      clients.get(hostAndPort).asInstanceOf[T]
    } else {
      f(new FirstMeetProxyStrategy(), ServiceFramwork.injector.getInstance(classOf[HttpTransportService]))
    }
  }

  def buildIfPresent[T](hostAndPort: String, ps: ProxyStrategy, http: HttpTransportService)(implicit manifest: Manifest[T]) = {
    if (clients.containsKey(hostAndPort)) {
      clients.get(hostAndPort).asInstanceOf[T]
    } else {
      buildClient(List(hostAndPort), ps, http)
    }
  }

  def buildClient[T](hostAndPortList: List[String], proxyStrategy: ProxyStrategy, transportService: HttpTransportService)(implicit manifest: Manifest[T]): T = {
    import scala.collection.JavaConversions._
    val items: java.util.List[RestClientProxy] = hostAndPortList.map { target =>
      val restClientProxy = new RestClientProxy(transportService)
      restClientProxy.target("http://" + target + "/")
      restClientProxy
    }
    val cluster: ClusterRestClientProxy = new ClusterRestClientProxy(items, proxyStrategy)
    val clazz = manifest.runtimeClass
    Proxy.newProxyInstance(clazz.getClassLoader, Array(clazz), cluster).asInstanceOf[T]
  }

}

