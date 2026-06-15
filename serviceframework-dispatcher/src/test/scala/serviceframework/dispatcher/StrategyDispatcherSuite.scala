package serviceframework.dispatcher

import java.util

import net.csdn.common.settings.ImmutableSettings.settingsBuilder
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import serviceframework.dispatcher.default.{ABTestStrategy, LinearStrategy}

import scala.collection.JavaConverters._

class StrategyDispatcherSuite extends AnyFunSuite with Matchers {

  test("loads processors, refs, and dispatches the default app strategy") {
    val dispatcher = new StrategyDispatcher[String](settingsBuilder.build())
    dispatcher.configShortNameMapping(new TestShortNameMapping)
    dispatcher.loadConfig(
      s"""
         |{
         |  "base": {
         |    "strategy": "linear",
         |    "processor": [{"name": "baseProcessor"}],
         |    "configParams": {"topic": ["news", "sports"]}
         |  },
         |  "app": {
         |    "strategy": "linear",
         |    "processor": [{"name": "appProcessor"}],
         |    "ref": ["base"]
         |  }
         |}
         |""".stripMargin)

    val params = new util.HashMap[Any, Any]()
    val result = dispatcher.dispatch(params)

    result.asScala.toList shouldEqual List("appProcessor")
    params.containsKey("_cache_") shouldBe true
    params.containsKey("_token_") shouldBe true
    dispatcher.strategies.get("app").ref.get(0).name shouldEqual "base"
  }

  test("findStrategies groups strategies by topic config using Java collections") {
    val settings = settingsBuilder
      .put("strategy.dispatcher.topic.enable", "true")
      .build()
    val dispatcher = new StrategyDispatcher[String](settings)
    dispatcher.configShortNameMapping(new TestShortNameMapping)
    dispatcher.loadConfig(
      s"""
         |{
         |  "newsStrategy": {
         |    "strategy": "linear",
         |    "processor": [{"name": "newsProcessor"}],
         |    "configParams": {"topic": ["news", "shared"]}
         |  },
         |  "sportsStrategy": {
         |    "strategy": "linear",
         |    "processor": [{"name": "sportsProcessor"}],
         |    "configParams": {"topic": ["sports", "shared"]}
         |  },
         |  "untagged": {
         |    "strategy": "linear",
         |    "processor": [{"name": "untaggedProcessor"}]
         |  }
         |}
         |""".stripMargin)

    dispatcher.findStrategies("news").get.map(_.name) shouldEqual List("newsStrategy")
    dispatcher.findStrategies("shared").get.map(_.name).toSet shouldEqual Set("newsStrategy", "sportsStrategy")
    dispatcher.findStrategies("missing") shouldBe None
  }

  test("chain share dispatch runs every topic strategy and shares cache") {
    val settings = settingsBuilder
      .put("strategy.dispatcher.topic.enable", "true")
      .put("strategy.dispatcher.chain.share.enable", "true")
      .build()
    val dispatcher = new StrategyDispatcher[String](settings)
    dispatcher.configShortNameMapping(new TestShortNameMapping)
    dispatcher.loadConfig(
      s"""
         |{
         |  "first": {
         |    "strategy": "cacheAware",
         |    "configParams": {"topic": ["shared"]}
         |  },
         |  "second": {
         |    "strategy": "cacheAware",
         |    "configParams": {"topic": ["shared"]}
         |  }
         |}
         |""".stripMargin)

    val params = new util.HashMap[Any, Any]()
    params.put("_client_", "shared")

    dispatcher.dispatch(params).asScala.toList shouldEqual List("first", "second:first")
  }

  test("linear strategy loads and applies compositor pipeline from config") {
    val dispatcher = new StrategyDispatcher[String](settingsBuilder.build())
    dispatcher.configShortNameMapping(new TestShortNameMapping)
    dispatcher.loadConfig(
      s"""
         |{
         |  "app": {
         |    "strategy": "linear",
         |    "processor": [{"name": "seed"}],
         |    "compositor": [
         |      {"name": "append", "typeFilter": ["all"], "params": [{"suffix": "|first"}]},
         |      {"name": "append", "typeFilter": ["all"], "params": [{"suffix": "|second"}]}
         |    ]
         |  }
         |}
         |""".stripMargin)

    dispatcher.dispatch(new util.HashMap[Any, Any]()).asScala.toList shouldEqual List("seed|first|second")
  }

  test("AB strategy keeps initialized processors and honors weighted refs") {
    val left = new FixedStrategy("left", "L")
    val right = new FixedStrategy("right", "R")
    val refs = new util.ArrayList[Strategy[String]]()
    refs.add(left)
    refs.add(right)

    val processors = new util.ArrayList[Processor[String]]()
    processors.add(new NamedProcessor[String])

    val params = new util.HashMap[Any, Any]()
    params.put("cid", "userId")
    params.put("left", Double.box(1.0d))

    val strategy = new ABTestStrategy[String]()
    strategy.initialize("ab", processors, refs, new util.ArrayList[Compositor[String]](), params)

    strategy.processor shouldBe theSameInstanceAs(processors)

    val request = new util.HashMap[Any, Any]()
    request.put("userId", "any-user")
    strategy.result(request).asScala.toList shouldEqual List("L")

    params.put("left", Double.box(0.0d))
    strategy.result(request).asScala.toList shouldEqual List("R")
  }

  test("AB strategy rejects requests without the configured cid") {
    val refs = new util.ArrayList[Strategy[String]]()
    refs.add(new FixedStrategy("left", "L"))
    refs.add(new FixedStrategy("right", "R"))

    val params = new util.HashMap[Any, Any]()
    params.put("cid", "userId")

    val strategy = new ABTestStrategy[String]()
    strategy.initialize("ab", new util.ArrayList[Processor[String]](), refs, new util.ArrayList[Compositor[String]](), params)

    val thrown = intercept[IllegalArgumentException] {
      strategy.result(new util.HashMap[Any, Any]())
    }
    thrown.getMessage should include("userId")
  }
}

class TestShortNameMapping extends ShortNameMapping {
  override def forName(shortName: String): String = shortName match {
    case "linear" => classOf[LinearStrategy[String]].getName
    case "cacheAware" => classOf[CacheAwareStrategy].getName
    case "append" => classOf[AppendingCompositor[String]].getName
    case _ => classOf[NamedProcessor[String]].getName
  }
}

class NamedProcessor[T] extends Processor[T] {
  private var _name: String = _

  override def initialize(name: String, params: util.List[util.Map[Any, Any]]): Unit = {
    _name = name
  }

  override def result(params: util.Map[Any, Any]): util.List[T] = {
    val result = new util.ArrayList[T]()
    result.add(_name.asInstanceOf[T])
    result
  }

  override def name(): String = _name
}

class FixedStrategy(private val strategyName: String, value: String) extends Strategy[String] {
  override def processor: util.List[Processor[String]] = new util.ArrayList[Processor[String]]()

  override def ref: util.List[Strategy[String]] = new util.ArrayList[Strategy[String]]()

  override def compositor: util.List[Compositor[String]] = new util.ArrayList[Compositor[String]]()

  override def name: String = strategyName

  override def initialize(name: String,
                          alg: util.List[Processor[String]],
                          ref: util.List[Strategy[String]],
                          com: util.List[Compositor[String]],
                          params: util.Map[Any, Any]): Unit = {}

  override def result(params: util.Map[Any, Any]): util.List[String] = {
    val result = new util.ArrayList[String]()
    result.add(value)
    result
  }

  override def configParams: util.Map[Any, Any] = new util.HashMap[Any, Any]()
}

class CacheAwareStrategy extends Strategy[String] {
  private var strategyName: String = _
  private var config: util.Map[Any, Any] = _

  override def processor: util.List[Processor[String]] = new util.ArrayList[Processor[String]]()

  override def ref: util.List[Strategy[String]] = new util.ArrayList[Strategy[String]]()

  override def compositor: util.List[Compositor[String]] = new util.ArrayList[Compositor[String]]()

  override def name: String = strategyName

  override def initialize(name: String,
                          alg: util.List[Processor[String]],
                          ref: util.List[Strategy[String]],
                          com: util.List[Compositor[String]],
                          params: util.Map[Any, Any]): Unit = {
    strategyName = name
    config = params
  }

  override def result(params: util.Map[Any, Any]): util.List[String] = {
    val cache = params.get("_cache_").asInstanceOf[util.Map[Any, Any]]
    val result = new util.ArrayList[String]()
    if (strategyName == "first") {
      cache.put("marker", strategyName)
      result.add(strategyName)
    } else {
      result.add(strategyName + ":" + cache.get("marker"))
    }
    result
  }

  override def configParams: util.Map[Any, Any] = config
}

class AppendingCompositor[T] extends Compositor[T] {
  private var configParams: util.List[util.Map[Any, Any]] = _

  override def initialize(typeFilters: util.List[String], configParams: util.List[util.Map[Any, Any]]): Unit = {
    this.configParams = configParams
  }

  override def result(alg: util.List[Processor[T]],
                      ref: util.List[Strategy[T]],
                      middleResult: util.List[T],
                      params: util.Map[Any, Any]): util.List[T] = {
    val source = if (middleResult == null) {
      alg.get(0).result(params).get(0).toString
    } else {
      middleResult.get(0).toString
    }
    val suffix = configParams.get(0).get("suffix").toString
    val result = new util.ArrayList[T]()
    result.add((source + suffix).asInstanceOf[T])
    result
  }
}
