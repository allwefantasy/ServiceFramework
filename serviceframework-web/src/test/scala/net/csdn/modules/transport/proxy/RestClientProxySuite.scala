package net.csdn.modules.transport.proxy

import java.lang.reflect.Method
import java.util
import java.util.concurrent.FutureTask

import com.alibaba.dubbo.rpc.protocol.rest.RestClientProxy
import net.csdn.common.collect.Tuple
import net.csdn.common.path.Url
import net.csdn.modules.http.RestRequest
import net.csdn.modules.transport.HttpTransportService
import net.csdn.modules.transport.HttpTransportService.SResponse
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.collection.JavaConverters._

class RestClientProxySuite extends AnyFunSuite with Matchers {

  test("RestClientProxy converts Scala and Java maps into GET query params") {
    val transport = new RecordingTransport
    val proxy = new RestClientProxy(transport)
    proxy.target("http://127.0.0.1:9000")
    val method = classOf[TestRestApi].getMethod(
      "fetch",
      classOf[String],
      classOf[scala.collection.immutable.Map[String, String]],
      classOf[java.util.Map[String, String]])

    val javaParams = new util.HashMap[String, String]()
    javaParams.put("sort", "desc")

    val response = proxy.invoke(
      null,
      method,
      Array[AnyRef]("42", Map("q" -> "scala map"), javaParams).asInstanceOf[Array[AnyRef]])

    response.asInstanceOf[SResponse].getStatus shouldEqual 200
    transport.lastGetUrl.getPath shouldEqual "/items/42"
    transport.lastGetParams.asScala.toMap shouldEqual Map("q" -> "scala map", "sort" -> "desc")
  }

  test("RestClientProxy writes body requests through http with encoded query string") {
    val transport = new RecordingTransport
    val proxy = new RestClientProxy(transport)
    proxy.target("http://127.0.0.1:9000")
    val method = classOf[TestRestApi].getMethod(
      "save",
      classOf[String],
      classOf[java.util.Map[String, String]],
      classOf[String],
      classOf[RestRequest.Method])

    val params = new util.HashMap[String, String]()
    params.put("q", "a b")

    proxy.invoke(null, method, Array[AnyRef]("99", params, """{"ok":true}""", RestRequest.Method.PUT))

    transport.lastHttpMethod shouldEqual RestRequest.Method.PUT
    transport.lastHttpUrl.getPath shouldEqual "/items/99"
    transport.lastHttpUrl.getQuery shouldEqual "q=a+b"
    transport.lastHttpBody shouldEqual """{"ok":true}"""
  }

  test("RestClientProxy posts form params when no body is supplied") {
    val transport = new RecordingTransport
    val proxy = new RestClientProxy(transport)
    proxy.target("http://127.0.0.1:9000")
    val method = classOf[TestRestApi].getMethod(
      "save",
      classOf[String],
      classOf[java.util.Map[String, String]],
      classOf[String],
      classOf[RestRequest.Method])

    val params = new util.HashMap[String, String]()
    params.put("q", "form")

    proxy.invoke(null, method, Array[AnyRef]("77", params, null, RestRequest.Method.POST))

    transport.lastPostUrl.getPath shouldEqual "/items/77"
    transport.lastPostParams.asScala.toMap shouldEqual Map("q" -> "form")
    transport.lastHttpUrl shouldBe null
  }

  test("RestClientProxy rejects methods not declared by the At annotation") {
    val proxy = new RestClientProxy(new RecordingTransport)
    proxy.target("http://127.0.0.1:9000")
    val method = classOf[TestRestApi].getMethod(
      "save",
      classOf[String],
      classOf[java.util.Map[String, String]],
      classOf[String],
      classOf[RestRequest.Method])

    val thrown = intercept[RuntimeException] {
      proxy.invoke(
        null,
        method,
        Array[AnyRef]("77", new util.HashMap[String, String](), null, RestRequest.Method.DELETE))
    }
    thrown.getMessage should include("DELETE")
  }

  test("AggregateRestClient builds one proxy per host and delegates to the strategy") {
    val transport = new RecordingTransport
    var seenHosts = List.empty[String]
    val strategy = new ProxyStrategy {
      override def invoke(proxyList: util.List[RestClientProxy],
                          o: Object,
                          method: Method,
                          objects: Array[Object]): util.List[SResponse] = {
        seenHosts = proxyList.asScala.map(_.hostAndPort()).toList
        val responses = new util.ArrayList[SResponse]()
        responses.add(new SResponse(200, "ok", new Url("http://127.0.0.1:9001/ping")))
        responses
      }
    }

    val api = AggregateRestClient.buildClient[TestRestApi](
      List("127.0.0.1:9001", "127.0.0.1:9002"),
      strategy,
      transport)

    val responses = api.ping()

    seenHosts shouldEqual List("127.0.0.1:9001", "127.0.0.1:9002")
    responses.size() shouldEqual 1
    responses.get(0).getContent shouldEqual "ok"
  }
}

class RecordingTransport extends HttpTransportService {
  var lastGetUrl: Url = _
  var lastGetParams: util.Map[String, String] = _
  var lastPostUrl: Url = _
  var lastPostParams: util.Map[_, _] = _
  var lastHttpUrl: Url = _
  var lastHttpBody: String = _
  var lastHttpMethod: RestRequest.Method = _

  override def get(url: Url, data: util.Map[String, String]): SResponse = {
    lastGetUrl = url
    lastGetParams = new util.HashMap[String, String](data)
    new SResponse(200, "get", url)
  }

  override def http(url: Url, jsonData: String, method: RestRequest.Method): SResponse = {
    lastHttpUrl = url
    lastHttpBody = jsonData
    lastHttpMethod = method
    new SResponse(201, "http", url)
  }

  override def post(url: Url, data: util.Map[_, _]): SResponse = {
    lastPostUrl = url
    lastPostParams = new util.HashMap[Any, Any](data.asInstanceOf[util.Map[Any, Any]])
    new SResponse(202, "post", url)
  }

  override def post(url: Url, data: util.Map[_, _], headers: util.Map[String, String]): SResponse =
    throw new UnsupportedOperationException

  override def post(url: Url, data: util.Map[_, _], timeout: Int): SResponse =
    throw new UnsupportedOperationException

  override def post(url: Url, data: util.Map[_, _], headers: util.Map[String, String], timeout: Int): SResponse =
    throw new UnsupportedOperationException

  override def get(url: Url, timeout: Int): SResponse =
    throw new UnsupportedOperationException

  override def get(url: Url): SResponse =
    throw new UnsupportedOperationException

  override def get(url: Url, data: util.Map[String, String], timeout: Int): SResponse =
    throw new UnsupportedOperationException

  override def put(url: Url, data: util.Map[_, _]): SResponse =
    throw new UnsupportedOperationException

  override def put(url: Url, data: util.Map[_, _], headers: util.Map[String, String]): SResponse =
    throw new UnsupportedOperationException

  override def http(url: Url, jsonData: String, headers: util.Map[String, String], method: RestRequest.Method): SResponse =
    throw new UnsupportedOperationException

  override def http(url: Url, jsonData: String, method: RestRequest.Method, timeout: Int): SResponse =
    throw new UnsupportedOperationException

  override def http(url: Url,
                    jsonData: String,
                    headers: util.Map[String, String],
                    method: RestRequest.Method,
                    timeout: Int): SResponse =
    throw new UnsupportedOperationException

  override def asyncHttp(url: Url, jsonData: String, method: RestRequest.Method): FutureTask[SResponse] =
    throw new UnsupportedOperationException

  override def header(header: String, value: String): Unit = {}

  override def asyncHttps(urls: util.List[Url], jsonData: String, method: RestRequest.Method): util.List[SResponse] =
    throw new UnsupportedOperationException

  override def asyncHttps(urlWithPostString: util.Map[Url, String],
                          method: RestRequest.Method,
                          timeout: Int): util.List[Tuple[Url, SResponse]] =
    throw new UnsupportedOperationException
}
