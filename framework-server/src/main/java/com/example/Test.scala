package com.example

import net.csdn.ServiceFramwork
import net.csdn.modules.transport.HttpTransportService
import net.csdn.modules.http.RestRequest
import net.csdn.common.path.Url

/**
 * 7/17/14 WilliamZhu(allwefantasy@gmail.com)
 */
class Test

object Test extends net.csdn.bootstrap.Application {
  def main(args: Array[String]): Unit = {
    ServiceFramwork.scanService.setLoader(classOf[Test])
    ServiceFramwork.disableHTTP()
    ServiceFramwork.disableThrift()
    net.csdn.bootstrap.Application.main(args)
    val transport = ServiceFramwork.injector.getInstance(classOf[HttpTransportService])
    Range(0, 100000).par.foreach {
      f =>
        transport.http(new Url("http://127.0.0.1:9002/say/hello"), null, RestRequest.Method.GET)
    }
  }
}
