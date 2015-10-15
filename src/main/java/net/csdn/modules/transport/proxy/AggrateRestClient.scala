package net.csdn.modules.transport.proxy

import java.lang.reflect.Proxy

import com.alibaba.dubbo.rpc.protocol.rest.RestClientProxy
import net.csdn.common.settings.{ImmutableSettings, Settings}
import net.csdn.modules.threadpool.DefaultThreadPoolService
import net.csdn.modules.transport.{DefaultHttpTransportService, HttpTransportService}

/**
 * 10/14/15 WilliamZhu(allwefantasy@gmail.com)
 */

object AggregateRestClient {


  def buildTransportService = {
    val settings: Settings = ImmutableSettings.settingsBuilder().loadFromClasspath("application.yml").build()
    new DefaultHttpTransportService(new DefaultThreadPoolService(settings), settings)
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

