package serviceframework.dispatcher

import java.io.{ByteArrayOutputStream, InputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import java.util.concurrent.{CountDownLatch, TimeUnit}

import net.csdn.common.settings.ImmutableSettings.settingsBuilder
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import serviceframework.dispatcher.default.LinearStrategy

import scala.collection.JavaConverters._

class StrategyDispatcherLifecycleSuite extends AnyFunSuite with Matchers {

  test("reload replaces the graph and removes strategies that disappeared") {
    val dispatcher = newDispatcher()
    dispatcher.loadConfig(tokenConfig("app", "v1", "extra", "gone"))
    val oldApp = dispatcher.strategies.get("app").asInstanceOf[TrackStrategy]
    val extra = dispatcher.strategies.get("extra").asInstanceOf[TrackStrategy]

    dispatcher.reload(tokenConfig("app", "v2"))

    dispatcher.dispatch(new util.HashMap[Any, Any]()).asScala.toList shouldEqual List("v2")
    dispatcher.strategies.get("extra") shouldBe null
    dispatcher.strategies.get("app") should not be theSameInstanceAs(oldApp)
    oldApp.stops.get() shouldBe 1
    extra.stops.get() shouldBe 1
    dispatcher.strategies.get("app").asInstanceOf[TrackStrategy].stops.get() shouldBe 0
  }

  test("failed reload keeps the published graph and does not stop it") {
    val dispatcher = newDispatcher()
    dispatcher.loadConfig(tokenConfig("app", "v1"))
    val oldApp = dispatcher.strategies.get("app").asInstanceOf[TrackStrategy]

    val thrown = intercept[StrategyLoadException] {
      dispatcher.reload("""{"app":{"strategy":"ghostType","ref":["missing"]}}""")
    }

    thrown.getMessage should include("missing")
    thrown.getMessage should include("app.ref[0]")
    thrown.getMessage should not include("unexpected class resolve")
    dispatcher.strategies.get("app") shouldBe theSameInstanceAs(oldApp)
    oldApp.stops.get() shouldBe 0
    dispatcher.dispatch(new util.HashMap[Any, Any]()).asScala.toList shouldEqual List("v1")
  }

  test("reload initialization failure releases only the candidate") {
    StopCountProcessor.constructed.set(0)
    val dispatcher = newDispatcher()
    dispatcher.loadConfig(tokenConfig("app", "v1"))
    val oldApp = dispatcher.strategies.get("app").asInstanceOf[TrackStrategy]

    val thrown = intercept[StrategyLoadException] {
      dispatcher.reload(
        """
          |{
          |  "app": {
          |    "strategy": "linear",
          |    "processor": [{"name": "stopCount"}, {"name": "initBoom"}]
          |  }
          |}
          |""".stripMargin)
    }

    thrown.getMessage should include("app.processor[1]")
    thrown.getMessage should include("初始化失败")
    thrown.getCause shouldBe a[IllegalStateException]
    thrown.getCause.getMessage should include("init-boom")
    StopCountProcessor.constructed.get() shouldBe 1
    StopCountProcessor.last.stops.get() shouldBe 1
    oldApp.stops.get() shouldBe 0
    dispatcher.dispatch(new util.HashMap[Any, Any]()).asScala.toList shouldEqual List("v1")
  }

  test("initialization close errors are suppressed on the original failure") {
    val dispatcher = newDispatcher()
    val thrown = intercept[StrategyLoadException] {
      dispatcher.loadConfig(
        """
          |{
          |  "app": {
          |    "strategy": "linear",
          |    "processor": [{"name": "stopBoom"}, {"name": "initBoom"}]
          |  }
          |}
          |""".stripMargin)
    }

    thrown.getMessage should include("app.processor[1]")
    thrown.getCause.getMessage should include("init-boom")
    thrown.getSuppressed.map(_.getMessage).mkString(" ") should include("stop-boom")
    dispatcher.strategies.isEmpty shouldBe true
    dispatcher.loadConfig(tokenConfig("app", "after"))
    dispatcher.dispatch(new util.HashMap[Any, Any]()).asScala.toList shouldEqual List("after")
  }

  test("missing ref, cycle, wrong type and constructor failures name the config path") {
    val missing = intercept[StrategyLoadException] {
      newDispatcher().loadConfig("""{"app":{"strategy":"ghostType","ref":["none"]}}""")
    }
    missing.strategyName shouldBe "app"
    missing.configPath shouldBe "app.ref[0]"
    missing.getMessage should include("不存在")
    missing.getMessage should not include("unexpected class resolve")

    val cycle = intercept[StrategyLoadException] {
      newDispatcher().loadConfig(
        """
          |{
          |  "a": {"strategy": "ghostType", "ref": ["b"]},
          |  "b": {"strategy": "ghostType", "ref": ["a"]}
          |}
          |""".stripMargin)
    }
    cycle.getMessage should include("a -> b -> a")
    cycle.getMessage should not include("unexpected class resolve")

    PlainExtension.born.set(0)
    TrackCompositor.born.set(0)
    val wrongStrategy = intercept[StrategyLoadException] {
      newDispatcher().loadConfig("""{"app":{"strategy":"plain"}}""")
    }
    wrongStrategy.getMessage should include("app.strategy")
    wrongStrategy.getMessage should include("不是")
    wrongStrategy.getMessage should include(classOf[Strategy[_]].getName)
    PlainExtension.born.get() shouldBe 0

    val wrongProcessor = intercept[StrategyLoadException] {
      newDispatcher().loadConfig(
        """
          |{"app":{"strategy":"linear","processor":[{"name":"named"},{"name":"plain"}]}}
          |""".stripMargin)
    }
    wrongProcessor.getMessage should include("app.processor[1]")
    wrongProcessor.getMessage should include(classOf[Processor[_]].getName)
    PlainExtension.born.get() shouldBe 0

    val wrongCompositor = intercept[StrategyLoadException] {
      newDispatcher().loadConfig(
        """
          |{"app":{"strategy":"linear","processor":[{"name":"named"}],
          | "compositor":[{"name":"c"},{"name":"plain"}]}}
          |""".stripMargin)
    }
    wrongCompositor.getMessage should include("app.compositor[1]")
    wrongCompositor.getMessage should include(classOf[Compositor[_]].getName)
    TrackCompositor.born.get() shouldBe 0
    PlainExtension.born.get() shouldBe 0

    HiddenCtor.born.set(0)
    val hidden = intercept[StrategyLoadException] {
      newDispatcher().loadConfig("""{"app":{"strategy":"hiddenCtor"}}""")
    }
    hidden.getMessage should include("无参构造不是 public")
    hidden.configPath shouldBe "app.strategy"
    HiddenCtor.born.get() shouldBe 0

    val ctor = intercept[StrategyLoadException] {
      newDispatcher().loadConfig(
        """
          |{
          |  "base": {"strategy": "linear", "processor": [{"name": "stopCount"}]},
          |  "app": {"strategy": "linear", "ref": ["base"], "processor": [{"name": "ctorBoom"}]}
          |}
          |""".stripMargin)
    }
    ctor.getMessage should include("app.processor[0]")
    ctor.getMessage should include("构造失败")
    ctor.getCause.getMessage should include("ctor-boom")
    StopCountProcessor.last.stops.get() shouldBe 1
  }

  test("disabled extensions are not resolved and disabled refs are explicit") {
    val dispatcher = newDispatcher()
    dispatcher.loadConfig(
      """
        |{
        |  "ghost": {"enable": false, "strategy": "ghostType", "processor": [{"name": "ghostType"}]},
        |  "app": {"strategy": "linear", "processor": [{"name": "named"}]}
        |}
        |""".stripMargin)

    dispatcher.strategies.get("ghost") shouldBe null
    dispatcher.dispatch(new util.HashMap[Any, Any]()).asScala.toList shouldEqual List("named")

    val thrown = intercept[StrategyLoadException] {
      newDispatcher().loadConfig(
        """
          |{
          |  "ghost": {"enabled": false, "strategy": "ghostType"},
          |  "app": {"strategy": "linear", "ref": ["ghost"], "processor": [{"name": "named"}]}
          |}
          |""".stripMargin)
    }
    thrown.getMessage should include("未启用")
    thrown.getMessage should include("ghost")
    thrown.getMessage should not include("unexpected class resolve")
  }

  test("duplicate names, duplicate refs and both processor keys are rejected") {
    val dispatcher = newDispatcher()
    dispatcher.loadConfig(tokenConfig("app", "v1"))
    val oldApp = dispatcher.strategies.get("app")

    val duplicate = intercept[StrategyLoadException] {
      dispatcher.reload(
        """
          |{
          |  "app": {"strategy": "track", "configParams": {"token": "first"}},
          |  "app": {"strategy": "track", "configParams": {"token": "second"}}
          |}
          |""".stripMargin)
    }
    duplicate.getMessage should include("重复")
    dispatcher.strategies.get("app") shouldBe theSameInstanceAs(oldApp)
    dispatcher.dispatch(new util.HashMap[Any, Any]()).asScala.toList shouldEqual List("v1")

    val repeatedRef = intercept[StrategyLoadException] {
      newDispatcher().loadConfig(
        """{"app":{"strategy":"linear","ref":["base","base"]},"base":{"strategy":"linear"}}""")
    }
    repeatedRef.getMessage should include("重复引用")
    repeatedRef.configPath shouldBe "app.ref[1]"

    val both = intercept[StrategyLoadException] {
      newDispatcher().loadConfig(
        """{"app":{"strategy":"linear","algorithm":[{"name":"named"}],"processor":[{"name":"named"}]}}""")
    }
    both.getMessage should include("algorithm")
    both.getMessage should include("processor")
  }

  test("algorithm alias and declaration order still wire refs") {
    val dispatcher = newDispatcher()
    dispatcher.loadConfig(
      """
        |{
        |  "app": {"strategy": "linear", "algorithm": [{"name": "named"}], "ref": ["base"]},
        |  "base": {"strategy": "linear", "processor": [{"name": "named"}]}
        |}
        |""".stripMargin)

    dispatcher.dispatch(new util.HashMap[Any, Any]()).asScala.toList shouldEqual List("named")
    val app = dispatcher.strategies.get("app")
    app.ref.get(0) shouldBe theSameInstanceAs(dispatcher.strategies.get("base"))
  }

  test("processor compositor and shared ref are stopped once in reverse order") {
    StopTrace.events.clear()
    val dispatcher = newDispatcher()
    dispatcher.loadConfig(
      """
        |{
        |  "base": {
        |    "strategy": "track",
        |    "processor": [{"name": "p"}],
        |    "compositor": [{"name": "c", "params": [{"id": "c"}]}]
        |  },
        |  "left": {"strategy": "track", "ref": ["base"]},
        |  "right": {"strategy": "track", "ref": ["base"]}
        |}
        |""".stripMargin)

    val base = dispatcher.strategies.get("base")
    dispatcher.strategies.get("left").ref.get(0) shouldBe theSameInstanceAs(base)
    dispatcher.strategies.get("right").ref.get(0) shouldBe theSameInstanceAs(base)

    dispatcher.close()
    dispatcher.close()

    StopTrace.events.asScala.toList shouldEqual List(
      "strategy:right", "strategy:left", "strategy:base", "compositor:c", "processor:p")
    StopTrace.events.asScala.count(_ == "strategy:base") shouldBe 1
    StopTrace.events.asScala.count(_ == "processor:p") shouldBe 1
    StopTrace.events.asScala.count(_ == "compositor:c") shouldBe 1
  }

  test("explicit dispatchers are isolated and clear closes only the default instance") {
    val left = newDispatcher()
    val right = newDispatcher()
    left.loadConfig(tokenConfig("app", "left"))
    right.loadConfig(tokenConfig("app", "right"))
    val leftStrategy = left.strategies.get("app").asInstanceOf[TrackStrategy]
    val rightStrategy = right.strategies.get("app").asInstanceOf[TrackStrategy]

    left.close()

    leftStrategy.stops.get() shouldBe 1
    rightStrategy.stops.get() shouldBe 0
    right.dispatch(new util.HashMap[Any, Any]()).asScala.toList shouldEqual List("right")
    left should not be theSameInstanceAs(right)

    StrategyDispatcher.clear
    val global = StrategyDispatcher.getOrCreate(tokenConfig("app", "global"), settingsBuilder.build(), new LifecycleNames)
    val globalStrategy = global.strategies.get("app").asInstanceOf[TrackStrategy]
    try {
      global.dispatch(new util.HashMap[Any, Any]()).asScala.toList shouldEqual List("global")
      StrategyDispatcher.getOrCreate(tokenConfig("app", "ignored")) shouldBe theSameInstanceAs(global)
      StrategyDispatcher.clear
      globalStrategy.stops.get() shouldBe 1
      rightStrategy.stops.get() shouldBe 0
      right.dispatch(new util.HashMap[Any, Any]()).asScala.toList shouldEqual List("right")
    } finally {
      StrategyDispatcher.clear
    }
  }

  test("unknown client returns an empty result instead of a null strategy") {
    val dispatcher = newDispatcher()
    dispatcher.loadConfig(tokenConfig("app", "v1"))
    val params = new util.HashMap[Any, Any]()
    params.put("_client_", "missing")

    dispatcher.findStrategies("missing") shouldBe None
    dispatcher.dispatch(params).size() shouldBe 0
    params.containsKey("_cache_") shouldBe true

    val topicSettings = settingsBuilder.put("strategy.dispatcher.topic.enable", "true").build()
    val topicDispatcher = new StrategyDispatcher[String](topicSettings)
    topicDispatcher.configShortNameMapping(new LifecycleNames)
    topicDispatcher.loadConfig(
      """{"news":{"strategy":"track","configParams":{"token":"news","topic":["news"]}}}""")
    val topicParams = new util.HashMap[Any, Any]()
    topicParams.put("_client_", "missing")
    topicDispatcher.findStrategies("missing") shouldBe None
    topicDispatcher.dispatch(topicParams).size() shouldBe 0
  }

  test("dispatch keeps the old graph alive until the request finishes") {
    DispatchGate.reset()
    val dispatcher = newDispatcher()
    dispatcher.loadConfig("""{"app":{"strategy":"blocking"}}""")
    val oldStrategy = dispatcher.strategies.get("app").asInstanceOf[BlockingStrategy]
    val firstResult = new AtomicReference[util.List[String]]()
    val firstError = new AtomicReference[Throwable]()
    val worker = new Thread(new Runnable {
      override def run(): Unit = {
        try {
          firstResult.set(dispatcher.dispatch(new util.HashMap[Any, Any]()))
        } catch {
          case thrown: Throwable => firstError.set(thrown)
        }
      }
    })
    worker.start()
    assert(DispatchGate.entered.await(10, TimeUnit.SECONDS))

    val reloadError = new AtomicReference[Throwable]()
    val reloadDone = new CountDownLatch(1)
    val reloadThread = new Thread(new Runnable {
      override def run(): Unit = {
        try {
          dispatcher.reload("""{"app":{"strategy":"linear","processor":[{"name":"named"}]}}""")
        } catch {
          case thrown: Throwable => reloadError.set(thrown)
        } finally {
          reloadDone.countDown()
        }
      }
    })
    reloadThread.start()
    assert(reloadDone.await(10, TimeUnit.SECONDS))
    reloadError.get() shouldBe null
    oldStrategy.stops.get() shouldBe 0
    DispatchGate.stops.get() shouldBe 0

    dispatcher.dispatch(new util.HashMap[Any, Any]()).asScala.toList shouldEqual List("named")
    oldStrategy.stops.get() shouldBe 0

    DispatchGate.release.countDown()
    worker.join(10000)
    firstError.get() shouldBe null
    firstResult.get().asScala.toList shouldEqual List("old")
    oldStrategy.stops.get() shouldBe 1
    dispatcher.strategies.get("app").asInstanceOf[LinearStrategy[String]].processor.get(0).name() shouldBe "named"
  }

  test("createStrategy rejects duplicates and disabled entries without dropping the current graph") {
    val dispatcher = newDispatcher()
    dispatcher.loadConfig(tokenConfig("base", "base"))
    val base = dispatcher.strategies.get("base")
    val created = dispatcher.createStrategy("app", map(
      "strategy" -> "linear",
      "processor" -> util.Arrays.asList(map("name" -> "named")),
      "ref" -> util.Arrays.asList("base")
    ))
    created.get.name shouldBe "app"
    dispatcher.strategies.get("app").ref.get(0) shouldBe theSameInstanceAs(base)

    val duplicate = intercept[StrategyLoadException] {
      dispatcher.createStrategy("base", map("strategy" -> "track", "configParams" -> map("token" -> "other")))
    }
    duplicate.getMessage should include("重复")
    dispatcher.strategies.get("base") shouldBe theSameInstanceAs(base)
    base.asInstanceOf[TrackStrategy].stops.get() shouldBe 0

    val disabled = intercept[StrategyLoadException] {
      dispatcher.createStrategy("ghost", map("strategy" -> "ghostType", "enable" -> java.lang.Boolean.FALSE))
    }
    disabled.getMessage should include("已禁用")
    disabled.getMessage should not include("unexpected class resolve")
    dispatcher.dispatch(client("app")).asScala.toList shouldEqual List("named")
  }

  test("throwsException still decides whether dispatch failures propagate") {
    val dispatcher = newDispatcher()
    dispatcher.loadConfig("""{"app":{"strategy":"linear","processor":[{"name":"boomResult"}]}}""")
    val previous = StrategyDispatcher.throwsException
    try {
      StrategyDispatcher.throwsException = true
      val thrown = intercept[IllegalStateException] {
        dispatcher.dispatch(new util.HashMap[Any, Any]())
      }
      thrown.getMessage should include("result-boom")

      StrategyDispatcher.throwsException = false
      dispatcher.dispatch(new util.HashMap[Any, Any]()).size() shouldBe 0
    } finally {
      StrategyDispatcher.throwsException = previous
    }
  }

  test("rejected extensions are not initialized and a context loader extension stays inside the type boundary") {
    resetProbes()
    val wrongStrategy = intercept[StrategyLoadException] {
      newDispatcher().loadConfig(probeConfig("strategy", "serviceframework.dispatcher.WrongTypeProbe"))
    }
    wrongStrategy.configPath shouldBe "app.strategy"
    wrongStrategy.getMessage should include("不是")
    wrongStrategy.getMessage should include(classOf[Strategy[_]].getName)
    probeInt("wrongType").get() shouldBe 0

    val wrongProcessor = intercept[StrategyLoadException] {
      newDispatcher().loadConfig(
        """{"app":{"strategy":"linear","processor":[{"name":"serviceframework.dispatcher.WrongTypeProbe"}]}}""")
    }
    wrongProcessor.configPath shouldBe "app.processor[0]"
    wrongProcessor.getMessage should include(classOf[Processor[_]].getName)
    probeInt("wrongType").get() shouldBe 0

    val wrongCompositor = intercept[StrategyLoadException] {
      newDispatcher().loadConfig(
        """{"app":{"strategy":"linear","processor":[{"name":"named"}],
          |"compositor":[{"name":"serviceframework.dispatcher.WrongTypeProbe"}]}}""".stripMargin)
    }
    wrongCompositor.configPath shouldBe "app.compositor[0]"
    wrongCompositor.getMessage should include(classOf[Compositor[_]].getName)
    probeInt("wrongType").get() shouldBe 0

    val abstractStrategy = intercept[StrategyLoadException] {
      newDispatcher().loadConfig(probeConfig("strategy", "serviceframework.dispatcher.AbstractStrategyProbe"))
    }
    abstractStrategy.configPath shouldBe "app.strategy"
    abstractStrategy.getMessage should include("抽象类型")
    probeInt("abstractStrategy").get() shouldBe 0

    val abstractProcessor = intercept[StrategyLoadException] {
      newDispatcher().loadConfig(
        """{"app":{"strategy":"linear","processor":[{"name":"serviceframework.dispatcher.AbstractProcessorProbe"}]}}""")
    }
    abstractProcessor.configPath shouldBe "app.processor[0]"
    abstractProcessor.getMessage should include("抽象类型")
    probeInt("abstractProcessor").get() shouldBe 0

    val abstractCompositor = intercept[StrategyLoadException] {
      newDispatcher().loadConfig(
        """{"app":{"strategy":"linear","processor":[{"name":"named"}],
          |"compositor":[{"name":"serviceframework.dispatcher.AbstractCompositorProbe"}]}}""".stripMargin)
    }
    abstractCompositor.configPath shouldBe "app.compositor[0]"
    abstractCompositor.getMessage should include("抽象类型")
    probeInt("abstractCompositor").get() shouldBe 0

    val hiddenType = intercept[StrategyLoadException] {
      newDispatcher().loadConfig(probeConfig("strategy", "serviceframework.dispatcher.PackagePrivateStrategyProbe"))
    }
    hiddenType.configPath shouldBe "app.strategy"
    hiddenType.getMessage should include("不是 public")
    hiddenType.getMessage should not include("无参构造")
    probeInt("packagePrivate").get() shouldBe 0

    val missingCtor = intercept[StrategyLoadException] {
      newDispatcher().loadConfig(probeConfig("strategy", "serviceframework.dispatcher.NoDefaultCtorProbe"))
    }
    missingCtor.configPath shouldBe "app.strategy"
    missingCtor.getMessage should include("缺少无参构造")
    missingCtor.getCause shouldBe a[NoSuchMethodException]
    probeInt("noDefaultCtor").get() shouldBe 0

    val framework = newDispatcher()
    framework.loadConfig(probeConfig("strategy", "serviceframework.dispatcher.GoodStrategyProbe", "framework-ok"))
    framework.dispatch(new util.HashMap[Any, Any]()).asScala.toList shouldEqual List("framework-ok")
    probeInt("good").get() shouldBe 1
    probeLoader() should not be null

    resetProbes()
    val parent = extensionParentLoader()
    val loader = new ExtensionDefiningLoader(parent, Set(
      "serviceframework.dispatcher.WrongTypeProbe",
      "serviceframework.dispatcher.AbstractStrategyProbe",
      "serviceframework.dispatcher.GoodStrategyProbe"
    ))
    val previous = Thread.currentThread().getContextClassLoader
    try {
      Thread.currentThread().setContextClassLoader(loader)
      val dispatcher = newDispatcher()
      val rejected = intercept[StrategyLoadException] {
        dispatcher.loadConfig(probeConfig("strategy", "serviceframework.dispatcher.WrongTypeProbe"))
      }
      rejected.getMessage should include("不是")
      rejected.getMessage should include(classOf[Strategy[_]].getName)
      probeInt("wrongType").get() shouldBe 0
      loader.requested.contains("serviceframework.dispatcher.WrongTypeProbe") shouldBe true

      val abstractRejected = intercept[StrategyLoadException] {
        dispatcher.loadConfig(probeConfig("strategy", "serviceframework.dispatcher.AbstractStrategyProbe"))
      }
      abstractRejected.getMessage should include("抽象类型")
      probeInt("abstractStrategy").get() shouldBe 0
      loader.requested.contains("serviceframework.dispatcher.AbstractStrategyProbe") shouldBe true
      dispatcher.strategies.isEmpty shouldBe true

      dispatcher.loadConfig(probeConfig("strategy", "serviceframework.dispatcher.GoodStrategyProbe", "loader-ok"))
      dispatcher.dispatch(new util.HashMap[Any, Any]()).asScala.toList shouldEqual List("loader-ok")
      probeInt("good").get() shouldBe 1
      probeLoader() shouldBe theSameInstanceAs(loader)
      loader.requested.contains("serviceframework.dispatcher.GoodStrategyProbe") shouldBe true
    } finally {
      Thread.currentThread().setContextClassLoader(previous)
    }
  }

  test("file load errors include the config path and a missing class cause") {
    val file = Files.createTempFile("strategy-v2", ".json")
    try {
      Files.write(file,
        """{"app":{"strategy":"com.serviceframework.missing.NoSuchStrategy"}}""".getBytes(StandardCharsets.UTF_8))
      val settings = settingsBuilder.put("application.strategy.config.file", file.toString).build()
      val dispatcher = new StrategyDispatcher[String](settings)
      dispatcher.configShortNameMapping(new LifecycleNames)
      val thrown = intercept[StrategyLoadException] {
        dispatcher.loadConfig(null)
      }
      thrown.getMessage should include(file.toString)
      thrown.getMessage should include("app.strategy")
      thrown.getCause shouldBe a[ClassNotFoundException]
      dispatcher.strategies.isEmpty shouldBe true
    } finally {
      Files.deleteIfExists(file)
    }
  }

  private def newDispatcher(): StrategyDispatcher[String] = {
    val dispatcher = new StrategyDispatcher[String](settingsBuilder.build())
    dispatcher.configShortNameMapping(new LifecycleNames)
    dispatcher
  }

  private def tokenConfig(pairs: String*): String = {
    val entries = pairs.grouped(2).map { pair =>
      s""""${pair.head}":{"strategy":"track","configParams":{"token":"${pair(1)}"}}"""
    }
    entries.mkString("{", ",", "}")
  }

  private def client(name: String): util.HashMap[Any, Any] = {
    val params = new util.HashMap[Any, Any]()
    params.put("_client_", name)
    params
  }

  private def map(entries: (String, Any)*): util.HashMap[String, Any] = {
    val result = new util.HashMap[String, Any]()
    entries.foreach { case (key, value) => result.put(key, value) }
    result
  }

  private def probeConfig(field: String, className: String, token: String = ""): String = {
    val params = if (token.isEmpty) "" else s""","configParams":{"token":"$token"}"""
    s"""{"app":{"$field":"$className"$params}}"""
  }

  private def probeClass(): Class[_] = Class.forName("serviceframework.dispatcher.InitProbe")

  private def probeInt(name: String): AtomicInteger = {
    probeClass().getField(name).get(null).asInstanceOf[AtomicInteger]
  }

  private def probeLoader(): ClassLoader = {
    probeClass().getField("loadedBy").get(null).asInstanceOf[ClassLoader]
  }

  private def resetProbes(): Unit = {
    probeClass().getMethod("reset").invoke(null)
  }

  private def extensionParentLoader(): ClassLoader = {
    val context = Thread.currentThread().getContextClassLoader
    if (context != null) context else getClass.getClassLoader
  }
}

private[dispatcher] class ExtensionDefiningLoader(parent: ClassLoader, owned: Set[String]) extends ClassLoader(parent) {
  val requested = new util.concurrent.CopyOnWriteArrayList[String]()

  override protected def loadClass(name: String, resolve: Boolean): Class[_] = {
    if (!owned.contains(name)) {
      return super.loadClass(name, resolve)
    }
    requested.add(name)
    synchronized {
      val lock = getClassLoadingLock(name)
      lock.synchronized {
        var loaded = findLoadedClass(name)
        if (loaded == null) {
          val resource = name.replace('.', '/') + ".class"
          val in = parent.getResourceAsStream(resource)
          if (in == null) {
            throw new ClassNotFoundException(name)
          }
          try {
            val bytes = ExtensionDefiningLoader.readFully(in)
            loaded = defineClass(name, bytes, 0, bytes.length)
          } finally {
            in.close()
          }
        }
        if (resolve) {
          resolveClass(loaded)
        }
        loaded
      }
    }
  }
}

private object ExtensionDefiningLoader {
  def readFully(in: InputStream): Array[Byte] = {
    val out = new ByteArrayOutputStream
    val buffer = new Array[Byte](4096)
    var n = in.read(buffer)
    while (n >= 0) {
      if (n > 0) out.write(buffer, 0, n)
      n = in.read(buffer)
    }
    out.toByteArray
  }
}

class LifecycleNames extends ShortNameMapping {
  override def forName(shortName: String): String = shortName match {
    case "linear" => classOf[LinearStrategy[String]].getName
    case "named" => classOf[NamedProcessor[String]].getName
    case "blocking" => classOf[BlockingStrategy].getName
    case "track" => classOf[TrackStrategy].getName
    case "p" => classOf[TrackProcessor].getName
    case "c" => classOf[TrackCompositor].getName
    case "stopCount" => classOf[StopCountProcessor].getName
    case "initBoom" => classOf[InitBoomProcessor].getName
    case "stopBoom" => classOf[StopBoomProcessor].getName
    case "ctorBoom" => classOf[CtorBoomProcessor].getName
    case "plain" => classOf[PlainExtension].getName
    case "hiddenCtor" => classOf[HiddenCtor].getName
    case "boomResult" => classOf[BoomResultProcessor].getName
    case other if other.contains('.') => other
    case other => throw new IllegalStateException("unexpected class resolve: " + other)
  }
}

class TrackStrategy extends Strategy[String] {
  private var strategyName: String = _
  private var config: util.Map[Any, Any] = _
  private var processors: util.List[Processor[String]] = _
  private var refs: util.List[Strategy[String]] = _
  private var compositors: util.List[Compositor[String]] = _
  val stops = new AtomicInteger()

  override def processor: util.List[Processor[String]] = processors
  override def ref: util.List[Strategy[String]] = refs
  override def compositor: util.List[Compositor[String]] = compositors
  override def name: String = strategyName
  override def configParams: util.Map[Any, Any] = config

  override def initialize(name: String,
                          alg: util.List[Processor[String]],
                          ref: util.List[Strategy[String]],
                          com: util.List[Compositor[String]],
                          params: util.Map[Any, Any]): Unit = {
    strategyName = name
    processors = alg
    refs = ref
    compositors = com
    config = params
  }

  override def result(params: util.Map[Any, Any]): util.List[String] = {
    val result = new util.ArrayList[String]()
    val token = if (config != null && config.containsKey("token")) config.get("token").toString else strategyName
    result.add(token)
    result
  }

  override def stop: Unit = {
    stops.incrementAndGet()
    StopTrace.events.add("strategy:" + strategyName)
  }
}

class TrackProcessor extends Processor[String] {
  private var processorName: String = _

  override def initialize(name: String, params: util.List[util.Map[Any, Any]]): Unit = {
    processorName = name
  }

  override def result(params: util.Map[Any, Any]): util.List[String] = new util.ArrayList[String]()

  override def name(): String = processorName

  override def stop: Unit = StopTrace.events.add("processor:" + processorName)
}

class TrackCompositor extends Compositor[String] {
  TrackCompositor.born.incrementAndGet()
  private var id: String = _

  override def initialize(typeFilters: util.List[String], configParams: util.List[util.Map[Any, Any]]): Unit = {
    if (configParams != null && !configParams.isEmpty) {
      id = String.valueOf(configParams.get(0).get("id"))
    }
  }

  override def result(alg: util.List[Processor[String]],
                      ref: util.List[Strategy[String]],
                      middleResult: util.List[String],
                      params: util.Map[Any, Any]): util.List[String] = new util.ArrayList[String]()

  override def stop: Unit = StopTrace.events.add("compositor:" + id)
}

object TrackCompositor {
  val born = new AtomicInteger()
}

class StopCountProcessor extends Processor[String] {
  StopCountProcessor.constructed.incrementAndGet()
  StopCountProcessor.last = this
  val stops = new AtomicInteger()
  private var processorName: String = _

  override def initialize(name: String, params: util.List[util.Map[Any, Any]]): Unit = {
    processorName = name
  }

  override def result(params: util.Map[Any, Any]): util.List[String] = new util.ArrayList[String]()

  override def name(): String = processorName

  override def stop: Unit = {
    stops.incrementAndGet()
  }
}

object StopCountProcessor {
  val constructed = new AtomicInteger()
  @volatile var last: StopCountProcessor = _
}

class InitBoomProcessor extends Processor[String] {
  override def initialize(name: String, params: util.List[util.Map[Any, Any]]): Unit = {
    throw new IllegalStateException("init-boom")
  }

  override def result(params: util.Map[Any, Any]): util.List[String] = new util.ArrayList[String]()

  override def name(): String = "initBoom"
}

class StopBoomProcessor extends Processor[String] {
  private var processorName: String = _

  override def initialize(name: String, params: util.List[util.Map[Any, Any]]): Unit = {
    processorName = name
  }

  override def result(params: util.Map[Any, Any]): util.List[String] = new util.ArrayList[String]()

  override def name(): String = processorName

  override def stop: Unit = throw new IllegalStateException("stop-boom")
}

class CtorBoomProcessor extends Processor[String] {
  throw new IllegalStateException("ctor-boom")

  override def initialize(name: String, params: util.List[util.Map[Any, Any]]): Unit = {}

  override def result(params: util.Map[Any, Any]): util.List[String] = new util.ArrayList[String]()

  override def name(): String = "ctorBoom"
}

class BoomResultProcessor extends Processor[String] {
  override def initialize(name: String, params: util.List[util.Map[Any, Any]]): Unit = {}

  override def result(params: util.Map[Any, Any]): util.List[String] = {
    throw new IllegalStateException("result-boom")
  }

  override def name(): String = "boomResult"
}

class PlainExtension {
  PlainExtension.born.incrementAndGet()
}

object PlainExtension {
  val born = new AtomicInteger()
}

class HiddenCtor private() extends TrackStrategy {
  HiddenCtor.born.incrementAndGet()
}

object HiddenCtor {
  val born = new AtomicInteger()
}

class BlockingStrategy extends Strategy[String] {
  private var strategyName: String = _
  private var config: util.Map[Any, Any] = _
  val stops = new AtomicInteger()

  override def processor: util.List[Processor[String]] = new util.ArrayList[Processor[String]]()
  override def ref: util.List[Strategy[String]] = new util.ArrayList[Strategy[String]]()
  override def compositor: util.List[Compositor[String]] = new util.ArrayList[Compositor[String]]()
  override def name: String = strategyName
  override def configParams: util.Map[Any, Any] = config

  override def initialize(name: String,
                          alg: util.List[Processor[String]],
                          ref: util.List[Strategy[String]],
                          com: util.List[Compositor[String]],
                          params: util.Map[Any, Any]): Unit = {
    strategyName = name
    config = params
  }

  override def result(params: util.Map[Any, Any]): util.List[String] = {
    DispatchGate.entered.countDown()
    if (!DispatchGate.release.await(20, TimeUnit.SECONDS)) {
      throw new IllegalStateException("dispatch gate timed out")
    }
    val result = new util.ArrayList[String]()
    result.add("old")
    result
  }

  override def stop: Unit = {
    stops.incrementAndGet()
    DispatchGate.stops.incrementAndGet()
  }
}

object DispatchGate {
  @volatile var entered: CountDownLatch = _
  @volatile var release: CountDownLatch = _
  val stops = new AtomicInteger()

  def reset(): Unit = {
    entered = new CountDownLatch(1)
    release = new CountDownLatch(1)
    stops.set(0)
  }
}

object StopTrace {
  val events = new util.concurrent.CopyOnWriteArrayList[String]()
}
