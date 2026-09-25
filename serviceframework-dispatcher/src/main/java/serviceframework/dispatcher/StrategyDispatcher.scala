package serviceframework.dispatcher

import java.lang.reflect.{InvocationTargetException, Modifier}
import java.util
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}
import java.util.{List => JList, Map => JMap, UUID}

import com.google.inject.{Inject, Singleton}
import net.csdn.common.env.Environment
import net.csdn.common.logging.Loggers
import net.csdn.common.settings.ImmutableSettings._
import net.csdn.common.settings.Settings
import net.sf.json.JSONObject

import scala.collection.JavaConverters._
import scala.collection.mutable


/**
  * 4/10/14 WilliamZhu(allwefantasy@gmail.com)
  *
  * 配置先整份解析并校验，再初始化候选图。全部成功后才替换当前图；
  * 正在执行的 dispatch 还持有旧图时不会 stop。
  */
@Singleton
class StrategyDispatcher[T] @Inject()(settings: Settings) {

  self =>
  private val logger = Loggers.getLogger(classOf[StrategyDispatcher[T]])
  private val mutationLock = new Object()
  private val instanceClosed = new AtomicBoolean(false)
  private val initialScope = new CloseScope(Array.empty[AnyRef])
  private val published = new AtomicReference[StrategyGraph](
    new StrategyGraph(initialScope, new ConcurrentHashMap[String, Strategy[T]](), new util.ArrayList[Strategy[T]]())
  )

  private var shortNameMapping: ShortNameMapping = new ShortNameMapping {
    override def forName(shortName: String): String = shortName
  }

  def strategies: ConcurrentHashMap[String, Strategy[T]] = published.get().strategies

  def dispatch(params: JMap[Any, Any]): JList[T] = {
    val graph = pinPublished()
    try {
      dispatchPinned(graph, params)
    } finally {
      graph.scope.unpinQuietly(logger)
    }
  }

  def help = {

  }

  def findStrategies(key: String): Option[List[Strategy[T]]] = {
    if (key == null) return None
    selectStrategies(published.get(), key)
  }

  def reload(configStr: String): Unit = replaceAll(configStr)

  def configShortNameMapping(mapping: ShortNameMapping): Unit = {
    if (mapping == null) {
      throw new IllegalArgumentException("ShortNameMapping 不能为空")
    }
    mutationLock.synchronized {
      shortNameMapping = mapping
    }
  }

  def loadConfig(configStr: String): Unit = replaceAll(configStr)

  /**
    * 增量注册一个策略。重名、显式禁用、引用不存在都会抛 [[StrategyLoadException]]，
    * 不再用 None 表示跳过。ref 必须已经在当前图中。失败只关闭本次新建的实例。
    */
  def createStrategy(name: String, desc: JMap[_, _]): Option[Strategy[T]] = {
    mutationLock.synchronized {
      ensureOpen()
      if (name == null || name.trim.isEmpty) {
        throw new StrategyLoadException("", "strategy", "策略名不能为空 配置[createStrategy]", null)
      }
      if (desc == null) {
        throw new StrategyLoadException(name, name, s"策略[$name] 路径[$name] 描述不能为空 配置[createStrategy]", null)
      }
      if (isDisabled(desc, name, "createStrategy")) {
        throw new StrategyLoadException(name, name, s"策略[$name] 已禁用，未加载实现类 配置[createStrategy]", null)
      }
      val current = published.get()
      if (current.strategies.containsKey(name)) {
        throw new StrategyLoadException(name, name, s"策略[$name] 重复注册 配置[createStrategy]", null)
      }
      val parsed = parseStrategy(name, desc, "createStrategy")
      parsed.refs.foreach { ref =>
        if (!current.strategies.containsKey(ref)) {
          throw new StrategyLoadException(name, s"$name.ref", s"策略[$name] 路径[$name.ref] 引用了尚未注册的策略[$ref] 配置[createStrategy]", null)
        }
      }
      val mapping = shortNameMapping
      validateClasses(Array(parsed), mapping, "createStrategy")
      val created = instantiateOne(parsed, current.strategies, mapping, "createStrategy")
      val map = new ConcurrentHashMap[String, Strategy[T]]()
      map.putAll(current.strategies)
      map.put(name, created.strategy)
      val ordered = new util.ArrayList[Strategy[T]](current.ordered)
      ordered.add(created.strategy)
      current.scope.addInstances(created.acquired)
      published.set(new StrategyGraph(current.scope, map, ordered))
      Some(created.strategy)
    }
  }

  /**
    * 幂等。反向关闭当前图里的策略、processor、compositor；共享实例只 stop 一次。
    * 若 dispatch 仍持有当前图，要等这些请求退出后才 stop。
    */
  def close(): Unit = {
    if (!instanceClosed.compareAndSet(false, true)) return
    mutationLock.synchronized {
      published.get().scope.retire()
    }
  }

  private def replaceAll(configStr: String): Unit = {
    mutationLock.synchronized {
      ensureOpen()
      val source = if (configStr != null) "inline" else settings.get("application.strategy.config.file", "strategy.v2.json")
      val text = readText(configStr, source)
      val parsed = parseConfig(text, source)
      val graph = instantiateAll(parsed, source)
      if (instanceClosed.get()) {
        graph.scope.retire()
        throw new IllegalStateException("StrategyDispatcher已关闭")
      }
      val old = published.getAndSet(graph)
      if (old != null && (old.scope ne graph.scope)) {
        old.scope.retire()
      }
    }
  }

  private def ensureOpen(): Unit = {
    if (instanceClosed.get()) {
      throw new IllegalStateException("StrategyDispatcher已关闭")
    }
  }

  private def readText(configStr: String, source: String): String = {
    if (configStr != null) {
      configStr
    } else {
      try {
        new Environment(settings).resolveConfigAndLoadToString(source)
      } catch {
        case t: Throwable =>
          throw new StrategyLoadException("", source, s"策略配置文件无法读取 路径[$source]", t)
      }
    }
  }

  private def pinPublished(): StrategyGraph = {
    var spins = 0
    while (spins < 1024) {
      spins += 1
      if (instanceClosed.get()) {
        throw new IllegalStateException("StrategyDispatcher已关闭")
      }
      val graph = published.get()
      if (graph != null && graph.scope.pin()) {
        if (instanceClosed.get()) {
          graph.scope.unpinQuietly(logger)
          throw new IllegalStateException("StrategyDispatcher已关闭")
        }
        return graph
      }
    }
    throw new IllegalStateException("StrategyDispatcher无法获取策略图")
  }

  private def dispatchPinned(graph: StrategyGraph, params: JMap[Any, Any]): JList[T] = {
    val clientType = if (params.containsKey("_client_")) params.get("_client_").asInstanceOf[String] else "app"
    params.put("_cache_", new util.HashMap[Any, Any]())
    params.put("_token_", if (params.containsKey("_token_")) params.get("_token_") else UUID.randomUUID().getMostSignificantBits() + "")
    selectStrategies(graph, clientType) match {
      case Some(strategies) =>
        val result = new util.ArrayList[T]()

        if (settings.getAsBoolean("strategy.dispatcher.chain.share.enable", false)) {
          val copyStr = JSONObject.fromObject(params).toString()

          try {
            val temp = JSONObject.fromObject(copyStr)
            val time = System.currentTimeMillis()
            result.addAll(strategies(0).result(temp.asInstanceOf[JMap[Any, Any]]))
            logger.info( s"""${params.get("_token_")} ${strategies(0).name} ${System.currentTimeMillis() - time}""")
            for (i <- 1 until strategies.size) {
              val temp2 = JSONObject.fromObject(copyStr)
              temp2.put("_cache_", temp.get("_cache_"))
              val time = System.currentTimeMillis()
              result.addAll(strategies(i).result(temp2.asInstanceOf[JMap[Any, Any]]))
              logger.info( s"""${params.get("_token_")} ${strategies(i).name} ${System.currentTimeMillis() - time}""")
            }
          } catch {
            case e: Exception =>
              logger.error("调用链路异常", e)
              if (StrategyDispatcher.throwsException) {
                throw e
              }
          }
          result
        } else {
          try {
            strategies.foreach { f =>
              val time = System.currentTimeMillis()
              result.addAll(f.result(params))
              logger.info( s"""${params.get("_token_")} ${f.name} ${System.currentTimeMillis() - time}""")
            }
          } catch {
            case e: Exception =>
              logger.error("调用链路异常", e)
              if (StrategyDispatcher.throwsException) {
                throw e
              }
          }
          result
        }

      case None => new util.ArrayList[T]()
    }
  }

  private def selectStrategies(graph: StrategyGraph, key: String): Option[List[Strategy[T]]] = {
    if (key == null || graph == null) return None
    if (!settings.getAsBoolean("strategy.dispatcher.topic.enable", false)) {
      val found = graph.strategies.get(key)
      if (found == null) None else Some(List(found))
    } else {
      val grouped = new util.LinkedHashMap[String, util.List[Strategy[T]]]()
      val iterator = graph.ordered.iterator()
      while (iterator.hasNext) {
        val strategy = iterator.next()
        val params = strategy.configParams
        if (params != null && params.containsKey("topic") && params.get("topic") != null) {
          params.get("topic") match {
            case topics: JList[_] =>
              val topicIterator = topics.iterator()
              while (topicIterator.hasNext) {
                val topic = String.valueOf(topicIterator.next())
                var bucket = grouped.get(topic)
                if (bucket == null) {
                  bucket = new util.ArrayList[Strategy[T]]()
                  grouped.put(topic, bucket)
                }
                bucket.add(strategy)
              }
            case _ =>
          }
        }
      }
      val found = grouped.get(key)
      if (found == null || found.isEmpty) {
        None
      } else {
        if (logger.isDebugEnabled) {
          found.asScala.foreach(f => logger.debug(s"获得消息链:${f.name}"))
        }
        Some(found.asScala.toList)
      }
    }
  }

  private def parseConfig(text: String, source: String): ParsedConfig = {
    if (text == null) {
      throw new StrategyLoadException("", source, s"策略配置为空 配置[$source]", null)
    }
    duplicateTopLevelKey(text) match {
      case Some(name) =>
        throw new StrategyLoadException(name, name, s"策略[$name] 重复名称 配置[$source]", null)
      case None =>
    }
    val json = try {
      JSONObject.fromObject(text)
    } catch {
      case t: Throwable =>
        throw new StrategyLoadException("", source, s"策略配置无法解析 配置[$source]", t)
    }
    if (json == null || json.isNullObject || !json.isInstanceOf[JSONObject]) {
      throw new StrategyLoadException("", source, s"策略配置必须是 JSON 对象 配置[$source]", null)
    }
    val names = json.names()
    val enabled = new util.ArrayList[ParsedStrategy]()
    val disabled = new util.HashSet[String]()
    if (names != null) {
      var i = 0
      while (i < names.size()) {
        val name = String.valueOf(names.get(i))
        val value = json.get(name)
        value match {
          case map: JMap[_, _] =>
            if (isDisabled(map, name, source)) {
              disabled.add(name)
            } else {
              enabled.add(parseStrategy(name, map, source))
            }
          case _ =>
            throw new StrategyLoadException(name, name, s"策略[$name] 路径[$name] 必须是对象 配置[$source]", null)
        }
        i += 1
      }
    }
    val parsed = enabled.asScala.toArray
    validateReferences(parsed, disabled, source)
    val orderedNames = parsed.map(_.name)
    ParsedConfig(parsed, topo(parsed, source), orderedNames)
  }

  private def parseStrategy(name: String, desc: JMap[_, _], source: String): ParsedStrategy = {
    if (!desc.containsKey("strategy") || desc.get("strategy") == null) {
      throw new StrategyLoadException(name, s"$name.strategy", s"策略[$name] 路径[$name.strategy] 必须包含 strategy 字段。该字段定义策略实现类 配置[$source]", null)
    }
    val strategyClass = asNonEmptyString(desc.get("strategy"), name, s"$name.strategy", "strategy 必须是非空字符串", source)
    if (desc.containsKey("algorithm") && desc.containsKey("processor")) {
      throw new StrategyLoadException(name, name, s"策略[$name] 不能同时声明 algorithm 和 processor 配置[$source]", null)
    }
    if (desc.containsKey("algorithm") && desc.get("algorithm") == null) {
      throw new StrategyLoadException(name, s"$name.algorithm", s"策略[$name] 路径[$name.algorithm] algorithm 不能为空 配置[$source]", null)
    }
    if (desc.containsKey("processor") && desc.get("processor") == null) {
      throw new StrategyLoadException(name, s"$name.processor", s"策略[$name] 路径[$name.processor] processor 不能为空 配置[$source]", null)
    }
    val hasAlgorithm = desc.containsKey("algorithm")
    val hasProcessor = desc.containsKey("processor")
    val processors = if (hasAlgorithm) {
      parseComponents(desc.get("algorithm"), name, s"$name.algorithm", source, compositor = false)
    } else if (hasProcessor) {
      parseComponents(desc.get("processor"), name, s"$name.processor", source, compositor = false)
    } else {
      Array.empty[ParsedComponent]
    }
    val compositors = if (desc.containsKey("compositor") && desc.get("compositor") != null) {
      parseComponents(desc.get("compositor"), name, s"$name.compositor", source, compositor = true)
    } else if (desc.containsKey("compositor") && desc.get("compositor") == null) {
      throw new StrategyLoadException(name, s"$name.compositor", s"策略[$name] 路径[$name.compositor] compositor 不能为空 配置[$source]", null)
    } else {
      Array.empty[ParsedComponent]
    }
    val refs = parseRefs(desc, name, source)
    val configParams = parseConfigParams(desc, name, source)
    ParsedStrategy(name, strategyClass, processors, compositors, refs, configParams)
  }

  private def parseComponents(raw: Any, strategy: String, path: String, source: String, compositor: Boolean): Array[ParsedComponent] = {
    raw match {
      case list: JList[_] =>
        val result = new Array[ParsedComponent](list.size())
        var i = 0
        while (i < list.size()) {
          val itemPath = s"$path[$i]"
          list.get(i) match {
            case item: JMap[_, _] =>
              if (!item.containsKey("name") || item.get("name") == null) {
                throw new StrategyLoadException(strategy, s"$itemPath.name", s"策略[$strategy] 路径[$itemPath.name] 必须包含 name 配置[$source]", null)
              }
              val className = asNonEmptyString(item.get("name"), strategy, s"$itemPath.name", "name 必须是非空字符串", source)
              val params = parseParams(item.get("params"), item.containsKey("params"), strategy, s"$itemPath.params", source)
              val typeFilters = if (compositor) parseTypeFilters(item, strategy, itemPath, source) else null
              result(i) = ParsedComponent(className, params, typeFilters)
            case _ =>
              throw new StrategyLoadException(strategy, itemPath, s"策略[$strategy] 路径[$itemPath] 必须是对象 配置[$source]", null)
          }
          i += 1
        }
        result
      case _ =>
        throw new StrategyLoadException(strategy, path, s"策略[$strategy] 路径[$path] 必须是数组 配置[$source]", null)
    }
  }

  private def parseParams(raw: Any, present: Boolean, strategy: String, path: String, source: String): JList[JMap[Any, Any]] = {
    if (!present || raw == null) return new util.ArrayList[JMap[Any, Any]]()
    raw match {
      case list: JList[_] =>
        val params = new util.ArrayList[JMap[Any, Any]]()
        var i = 0
        while (i < list.size()) {
          list.get(i) match {
            case item: JMap[_, _] =>
              params.add(item.asInstanceOf[JMap[Any, Any]])
            case _ =>
              throw new StrategyLoadException(strategy, s"$path[$i]", s"策略[$strategy] 路径[$path[$i]] 必须是对象 配置[$source]", null)
          }
          i += 1
        }
        params
      case _ =>
        throw new StrategyLoadException(strategy, path, s"策略[$strategy] 路径[$path] 必须是数组 配置[$source]", null)
    }
  }

  private def parseTypeFilters(item: JMap[_, _], strategy: String, itemPath: String, source: String): JList[String] = {
    if (!item.containsKey("typeFilter") || item.get("typeFilter") == null) return null
    item.get("typeFilter") match {
      case list: JList[_] =>
        val filters = new util.ArrayList[String]()
        var i = 0
        while (i < list.size()) {
          list.get(i) match {
            case text: String => filters.add(text)
            case _ =>
              throw new StrategyLoadException(strategy, s"$itemPath.typeFilter[$i]", s"策略[$strategy] 路径[$itemPath.typeFilter[$i]] 必须是字符串 配置[$source]", null)
          }
          i += 1
        }
        filters
      case _ =>
        throw new StrategyLoadException(strategy, s"$itemPath.typeFilter", s"策略[$strategy] 路径[$itemPath.typeFilter] 必须是数组 配置[$source]", null)
    }
  }

  private def parseRefs(desc: JMap[_, _], strategy: String, source: String): Array[String] = {
    if (!desc.containsKey("ref") || desc.get("ref") == null) {
      if (desc.containsKey("ref") && desc.get("ref") == null) {
        throw new StrategyLoadException(strategy, s"$strategy.ref", s"策略[$strategy] 路径[$strategy.ref] ref 不能为空 配置[$source]", null)
      }
      return Array.empty[String]
    }
    desc.get("ref") match {
      case list: JList[_] =>
        val refs = new Array[String](list.size())
        val seen = new util.HashSet[String]()
        var i = 0
        while (i < list.size()) {
          val ref = list.get(i) match {
            case text: String if text.nonEmpty => text
            case _ =>
              throw new StrategyLoadException(strategy, s"$strategy.ref[$i]", s"策略[$strategy] 路径[$strategy.ref[$i]] 必须是非空字符串 配置[$source]", null)
          }
          if (!seen.add(ref)) {
            throw new StrategyLoadException(strategy, s"$strategy.ref[$i]", s"策略[$strategy] 路径[$strategy.ref[$i]] 重复引用[$ref] 配置[$source]", null)
          }
          refs(i) = ref
          i += 1
        }
        refs
      case _ =>
        throw new StrategyLoadException(strategy, s"$strategy.ref", s"策略[$strategy] 路径[$strategy.ref] 必须是数组 配置[$source]", null)
    }
  }

  private def parseConfigParams(desc: JMap[_, _], strategy: String, source: String): JMap[Any, Any] = {
    if (!desc.containsKey("configParams") || desc.get("configParams") == null) {
      if (desc.containsKey("configParams") && desc.get("configParams") == null) {
        throw new StrategyLoadException(strategy, s"$strategy.configParams", s"策略[$strategy] 路径[$strategy.configParams] configParams 不能为空 配置[$source]", null)
      }
      return new util.HashMap[Any, Any]()
    }
    desc.get("configParams") match {
      case map: JMap[_, _] =>
        val params = map.asInstanceOf[JMap[Any, Any]]
        if (params.containsKey("topic") && params.get("topic") != null && !params.get("topic").isInstanceOf[JList[_]]) {
          throw new StrategyLoadException(strategy, s"$strategy.configParams.topic", s"策略[$strategy] 路径[$strategy.configParams.topic] topic 必须是数组 配置[$source]", null)
        }
        params
      case _ =>
        throw new StrategyLoadException(strategy, s"$strategy.configParams", s"策略[$strategy] 路径[$strategy.configParams] 必须是对象 配置[$source]", null)
    }
  }

  private def validateReferences(strategies: Array[ParsedStrategy], disabled: util.Set[String], source: String): Unit = {
    val enabled = strategies.map(_.name).toSet
    strategies.foreach { strategy =>
      strategy.refs.zipWithIndex.foreach { case (ref, index) =>
        if (disabled.contains(ref)) {
          throw new StrategyLoadException(strategy.name, s"${strategy.name}.ref[$index]", s"策略[${strategy.name}] 路径[${strategy.name}.ref[$index]] 引用了未启用的策略[$ref] 配置[$source]", null)
        }
        if (!enabled.contains(ref)) {
          throw new StrategyLoadException(strategy.name, s"${strategy.name}.ref[$index]", s"策略[${strategy.name}] 路径[${strategy.name}.ref[$index]] 引用了不存在的策略[$ref] 配置[$source]", null)
        }
      }
    }
  }

  private def topo(strategies: Array[ParsedStrategy], source: String): Array[ParsedStrategy] = {
    val byName = strategies.map(strategy => strategy.name -> strategy).toMap
    val index = strategies.iterator.zipWithIndex.map { case (strategy, position) => strategy.name -> position }.toMap
    val indegree = mutable.HashMap[String, Int]()
    val dependents = mutable.HashMap[String, mutable.ListBuffer[String]]()
    strategies.foreach { strategy =>
      indegree(strategy.name) = 0
    }
    strategies.foreach { strategy =>
      strategy.refs.foreach { ref =>
        indegree(strategy.name) = indegree(strategy.name) + 1
        dependents.getOrElseUpdate(ref, mutable.ListBuffer[String]()) += strategy.name
      }
    }
    val remaining = mutable.LinkedHashSet[String]()
    strategies.foreach(strategy => remaining.add(strategy.name))
    val ordered = new Array[ParsedStrategy](strategies.length)
    var cursor = 0
    while (remaining.nonEmpty) {
      val ready = remaining.iterator.filter(name => indegree(name) == 0).toList.sortBy(index)
      if (ready.isEmpty) {
        val cycle = cyclePath(strategies)
        val head = cycle.split(" -> ").headOption.getOrElse("")
        throw new StrategyLoadException(head, cycle, s"策略引用成环[$cycle] 配置[$source]", null)
      }
      val name = ready.head
      remaining.remove(name)
      ordered(cursor) = byName(name)
      cursor += 1
      dependents.getOrElse(name, mutable.ListBuffer.empty).foreach { dependent =>
        indegree(dependent) = indegree(dependent) - 1
      }
    }
    ordered
  }

  private def cyclePath(strategies: Array[ParsedStrategy]): String = {
    val refsOf = strategies.map(strategy => strategy.name -> strategy.refs.toList).toMap
    val visiting = mutable.LinkedHashSet[String]()
    val visited = mutable.HashSet[String]()
    var found: List[String] = Nil

    def dfs(name: String): Boolean = {
      if (visiting.contains(name)) {
        found = visiting.toList.dropWhile(_ != name) :+ name
        return true
      }
      if (visited.contains(name)) return false
      visiting.add(name)
      val hit = refsOf.getOrElse(name, Nil).exists(dfs)
      visiting.remove(name)
      visited.add(name)
      hit
    }

    strategies.exists(strategy => dfs(strategy.name))
    if (found.isEmpty) strategies.map(_.name).mkString(" -> ") else found.mkString(" -> ")
  }

  private def instantiateAll(parsed: ParsedConfig, source: String): StrategyGraph = {
    val mapping = shortNameMapping
    validateClasses(parsed.topo, mapping, source)
    val acquired = new util.ArrayList[AnyRef]()
    try {
      val byName = new util.LinkedHashMap[String, Strategy[T]]()
      parsed.topo.foreach { spec =>
        val created = instantiateOne(spec, byName, mapping, source)
        acquired.addAll(created.acquiredList)
        byName.put(spec.name, created.strategy)
      }
      val map = new ConcurrentHashMap[String, Strategy[T]]()
      val ordered = new util.ArrayList[Strategy[T]]()
      parsed.declaration.foreach { name =>
        val strategy = byName.get(name)
        map.put(name, strategy)
        ordered.add(strategy)
      }
      new StrategyGraph(new CloseScope(acquired.toArray(new Array[AnyRef](acquired.size()))), map, ordered)
    } catch {
      case t: Throwable =>
        val closing = StrategyDispatcher.closeInstances(acquired.toArray(new Array[AnyRef](acquired.size())))
        if (closing != null) t.addSuppressed(closing)
        throw t
    }
  }

  private def validateClasses(strategies: Array[ParsedStrategy], mapping: ShortNameMapping, source: String): Unit = {
    strategies.foreach { spec =>
      resolveAssignable(mapping, spec.strategyClass, classOf[Strategy[_]], spec.name, s"${spec.name}.strategy", source)
      spec.processors.zipWithIndex.foreach { case (component, index) =>
        resolveAssignable(mapping, component.className, classOf[Processor[_]], spec.name, s"${spec.name}.processor[$index]", source)
      }
      spec.compositors.zipWithIndex.foreach { case (component, index) =>
        resolveAssignable(mapping, component.className, classOf[Compositor[_]], spec.name, s"${spec.name}.compositor[$index]", source)
      }
    }
  }

  private def instantiateOne(spec: ParsedStrategy,
                             existing: JMap[String, Strategy[T]],
                             mapping: ShortNameMapping,
                             source: String): CreatedStrategy[T] = {
    val acquired = new util.ArrayList[AnyRef]()
    try {
      val processors = new util.ArrayList[Processor[T]]()
      var i = 0
      while (i < spec.processors.length) {
        val component = spec.processors(i)
        val path = s"${spec.name}.processor[$i]"
        val processor = construct(mapping, component.className, classOf[Processor[_]], spec.name, path, source).asInstanceOf[Processor[T]]
        acquired.add(processor)
        try {
          processor.initialize(component.className, component.params)
        } catch {
          case t: Throwable =>
            throw failure(source, spec.name, path, "初始化失败", t)
        }
        processors.add(processor)
        i += 1
      }
      val compositors = new util.ArrayList[Compositor[T]]()
      var j = 0
      while (j < spec.compositors.length) {
        val component = spec.compositors(j)
        val path = s"${spec.name}.compositor[$j]"
        val compositor = construct(mapping, component.className, classOf[Compositor[_]], spec.name, path, source).asInstanceOf[Compositor[T]]
        acquired.add(compositor)
        try {
          compositor.initialize(component.typeFilters, component.params)
        } catch {
          case t: Throwable =>
            throw failure(source, spec.name, path, "初始化失败", t)
        }
        compositors.add(compositor)
        j += 1
      }
      val refs = new util.ArrayList[Strategy[T]]()
      spec.refs.zipWithIndex.foreach { case (ref, index) =>
        val target = existing.get(ref)
        if (target == null) {
          throw failure(source, spec.name, s"${spec.name}.ref[$index]", s"引用了不存在的策略[$ref]", null)
        }
        refs.add(target)
      }
      val strategy = construct(mapping, spec.strategyClass, classOf[Strategy[_]], spec.name, s"${spec.name}.strategy", source).asInstanceOf[Strategy[T]]
      acquired.add(strategy)
      try {
        strategy.initialize(spec.name, processors, refs, compositors, spec.configParams)
      } catch {
        case t: Throwable =>
          throw failure(source, spec.name, spec.name, "初始化失败", t)
      }
      CreatedStrategy(strategy, acquired.toArray(new Array[AnyRef](acquired.size())), acquired)
    } catch {
      case t: Throwable =>
        val closing = StrategyDispatcher.closeInstances(acquired.toArray(new Array[AnyRef](acquired.size())))
        if (closing != null) t.addSuppressed(closing)
        throw t
    }
  }

  private def resolveAssignable(mapping: ShortNameMapping,
                                className: String,
                                expected: Class[_],
                                strategy: String,
                                path: String,
                                source: String): Class[_] = {
    val resolved = resolveName(mapping, className, strategy, path, source)
    val clazz = loadClass(resolved, strategy, path, source)
    if (!expected.isAssignableFrom(clazz)) {
      throw failure(source, strategy, path, s"类 $resolved 不是 ${expected.getName}，拒绝初始化", null)
    }
    if (!Modifier.isPublic(clazz.getModifiers)) {
      throw failure(source, strategy, path, s"类 $resolved 不是 public，拒绝初始化", null)
    }
    if (clazz.isInterface || Modifier.isAbstract(clazz.getModifiers)) {
      throw failure(source, strategy, path, s"类 $resolved 是抽象类型，拒绝初始化", null)
    }
    val ctor = try {
      clazz.getDeclaredConstructor()
    } catch {
      case t: Throwable =>
        throw failure(source, strategy, path, s"类 $resolved 缺少无参构造", t)
    }
    if (!Modifier.isPublic(ctor.getModifiers)) {
      throw failure(source, strategy, path, s"类 $resolved 的无参构造不是 public", null)
    }
    clazz
  }

  private def construct(mapping: ShortNameMapping,
                        className: String,
                        expected: Class[_],
                        strategy: String,
                        path: String,
                        source: String): AnyRef = {
    val clazz = resolveAssignable(mapping, className, expected, strategy, path, source)
    try {
      clazz.getDeclaredConstructor().newInstance().asInstanceOf[AnyRef]
    } catch {
      case t: InvocationTargetException =>
        throw failure(source, strategy, path, s"构造失败: ${clazz.getName}", if (t.getCause != null) t.getCause else t)
      case t: Throwable =>
        throw failure(source, strategy, path, s"构造失败: ${clazz.getName}", t)
    }
  }

  private def resolveName(mapping: ShortNameMapping, className: String, strategy: String, path: String, source: String): String = {
    try {
      val resolved = mapping.forName(className)
      if (resolved == null || resolved.trim.isEmpty) {
        throw failure(source, strategy, path, s"短名称解析结果为空: $className", null)
      }
      resolved
    } catch {
      case t: StrategyLoadException => throw t
      case t: Throwable =>
        throw failure(source, strategy, path, s"短名称解析失败: $className", t)
    }
  }

  private def loadClass(resolved: String, strategy: String, path: String, source: String): Class[_] = {
    try {
      // initialize=false：接口、可见性、抽象和无参构造通过之前不能跑 static initializer。
      Class.forName(resolved, false, selectClassLoader())
    } catch {
      case t: Throwable =>
        throw failure(source, strategy, path, s"找不到类: $resolved", t)
    }
  }

  private def selectClassLoader(): ClassLoader = {
    val context = Thread.currentThread().getContextClassLoader
    if (context != null) context
    else classOf[StrategyDispatcher[_]].getClassLoader
  }

  private def isDisabled(desc: JMap[_, _], strategy: String, source: String): Boolean = {
    val enable = optionalBoolean(desc, "enable", strategy, source).orElse(optionalBoolean(desc, "enabled", strategy, source))
    if (desc.containsKey("enable") && desc.containsKey("enabled")) {
      val left = optionalBoolean(desc, "enable", strategy, source)
      val right = optionalBoolean(desc, "enabled", strategy, source)
      if (left != right) {
        throw new StrategyLoadException(strategy, strategy, s"策略[$strategy] 的 enable 与 enabled 冲突 配置[$source]", null)
      }
    }
    val disable = optionalBoolean(desc, "disable", strategy, source).orElse(optionalBoolean(desc, "disabled", strategy, source))
    if (desc.containsKey("disable") && desc.containsKey("disabled")) {
      val left = optionalBoolean(desc, "disable", strategy, source)
      val right = optionalBoolean(desc, "disabled", strategy, source)
      if (left != right) {
        throw new StrategyLoadException(strategy, strategy, s"策略[$strategy] 的 disable 与 disabled 冲突 配置[$source]", null)
      }
    }
    (enable, disable) match {
      case (None, None) => false
      case (Some(true), None) => false
      case (Some(false), None) => true
      case (None, Some(true)) => true
      case (None, Some(false)) => false
      case (Some(true), Some(false)) => false
      case (Some(false), Some(true)) => true
      case _ =>
        throw new StrategyLoadException(strategy, strategy, s"策略[$strategy] 的启用标志冲突 配置[$source]", null)
    }
  }

  private def optionalBoolean(desc: JMap[_, _], key: String, strategy: String, source: String): Option[Boolean] = {
    if (!desc.containsKey(key) || desc.get(key) == null) return None
    desc.get(key) match {
      case value: java.lang.Boolean => Some(value.booleanValue())
      case value: String if value.equalsIgnoreCase("true") => Some(true)
      case value: String if value.equalsIgnoreCase("false") => Some(false)
      case _ =>
        throw new StrategyLoadException(strategy, s"$strategy.$key", s"策略[$strategy] 路径[$strategy.$key] 必须是布尔值 配置[$source]", null)
    }
  }

  private def asNonEmptyString(value: Any, strategy: String, path: String, detail: String, source: String): String = {
    value match {
      case text: String if text.trim.nonEmpty => text
      case _ =>
        throw new StrategyLoadException(strategy, path, s"策略[$strategy] 路径[$path] $detail 配置[$source]", null)
    }
  }

  private def failure(source: String, strategy: String, path: String, detail: String, cause: Throwable): StrategyLoadException = {
    cause match {
      case existing: StrategyLoadException => existing
      case _ =>
        new StrategyLoadException(strategy, path, s"策略[$strategy] 路径[$path] $detail 配置[$source]", cause)
    }
  }

  private def duplicateTopLevelKey(text: String): Option[String] = {
    val chars = text
    val length = chars.length
    var index = 0

    def skipWs(): Unit = {
      while (index < length && Character.isWhitespace(chars.charAt(index))) index += 1
    }

    def peek: Char = if (index < length) chars.charAt(index) else '\u0000'

    def parseString(): Option[String] = {
      if (peek != '"') return None
      index += 1
      val builder = new StringBuilder
      while (index < length) {
        val current = chars.charAt(index)
        index += 1
        current match {
          case '"' => return Some(builder.toString)
          case '\\' =>
            if (index >= length) return None
            val escaped = chars.charAt(index)
            index += 1
            escaped match {
              case '"' | '\\' | '/' => builder.append(escaped)
              case 'b' => builder.append('\b')
              case 'f' => builder.append('\f')
              case 'n' => builder.append('\n')
              case 'r' => builder.append('\r')
              case 't' => builder.append('\t')
              case 'u' =>
                if (index + 4 > length) return None
                val hex = chars.substring(index, index + 4)
                index += 4
                try {
                  builder.append(Integer.parseInt(hex, 16).toChar)
                } catch {
                  case _: NumberFormatException => return None
                }
              case _ => return None
            }
          case _ => builder.append(current)
        }
      }
      None
    }

    def consumeLiteral(literal: String): Boolean = {
      if (chars.startsWith(literal, index)) {
        index += literal.length
        true
      } else {
        false
      }
    }

    def skipNumber(): Boolean = {
      val start = index
      if (peek == '-') index += 1
      if (index >= length || !Character.isDigit(chars.charAt(index))) return false
      while (index < length && "0123456789+-.eE".indexOf(chars.charAt(index)) >= 0) index += 1
      index > start
    }

    def skipValue(): Boolean = {
      skipWs()
      if (index >= length) return false
      peek match {
        case '"' => parseString().isDefined
        case '{' =>
          index += 1
          skipContainer('}')
        case '[' =>
          index += 1
          skipContainer(']')
        case 't' => consumeLiteral("true")
        case 'f' => consumeLiteral("false")
        case 'n' => consumeLiteral("null")
        case '-' | '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' => skipNumber()
        case _ => false
      }
    }

    def skipContainer(end: Char): Boolean = {
      var first = true
      while (index < length) {
        skipWs()
        if (index >= length) return false
        if (peek == end) {
          index += 1
          return true
        }
        if (!first) {
          if (peek != ',') return false
          index += 1
          skipWs()
          if (index >= length || peek == end) return false
        }
        first = false
        if (end == '}' && peek == '"') {
          if (parseString().isEmpty) return false
          skipWs()
          if (peek != ':') return false
          index += 1
          if (!skipValue()) return false
        } else if (end == ']') {
          if (!skipValue()) return false
        } else {
          return false
        }
      }
      false
    }

    try {
      skipWs()
      if (peek != '{') return None
      index += 1
      val seen = new util.HashSet[String]()
      var first = true
      while (index < length) {
        skipWs()
        if (index >= length) return None
        if (peek == '}') return None
        if (!first) {
          if (peek != ',') return None
          index += 1
          skipWs()
          if (index >= length || peek == '}') return None
        }
        first = false
        val key = parseString() match {
          case Some(value) => value
          case None => return None
        }
        if (!seen.add(key)) return Some(key)
        skipWs()
        if (peek != ':') return None
        index += 1
        if (!skipValue()) return None
      }
      None
    } catch {
      case _: Throwable => None
    }
  }

  /**
    * 同一批还能被 dispatch 用到的实例。reload 换成新 scope 后，旧 scope 等 pin 归零再反向 stop。
    * 增量 createStrategy 继续沿用当前 scope，避免把正在执行的请求关掉。
    */
  private final class CloseScope(initial: Array[AnyRef]) {
    private val users = new AtomicInteger(0)
    private val retired = new AtomicBoolean(false)
    private val closed = new AtomicBoolean(false)
    private val acquired = new AtomicReference[Array[AnyRef]](initial)

    def addInstances(extra: Array[AnyRef]): Unit = {
      if (extra == null || extra.length == 0) return
      var done = false
      while (!done) {
        val current = acquired.get()
        val next = new Array[AnyRef](current.length + extra.length)
        System.arraycopy(current, 0, next, 0, current.length)
        System.arraycopy(extra, 0, next, current.length, extra.length)
        done = acquired.compareAndSet(current, next)
      }
    }

    def pin(): Boolean = {
      var pinned = false
      var finished = false
      while (!finished) {
        if (retired.get() || closed.get()) {
          pinned = false
          finished = true
        } else {
          val current = users.get()
          if (current < 0) {
            pinned = false
            finished = true
          } else if (users.compareAndSet(current, current + 1)) {
            if (retired.get() || closed.get()) {
              unpin()
              pinned = false
            } else {
              pinned = true
            }
            finished = true
          }
        }
      }
      pinned
    }

    def unpin(): Unit = {
      val left = users.decrementAndGet()
      if (left == 0 && retired.get()) closeNow()
    }

    def unpinQuietly(logger: net.csdn.common.logging.CSLogger): Unit = {
      try {
        unpin()
      } catch {
        case t: Throwable =>
          logger.error("关闭策略图失败", t)
      }
    }

    def retire(): Unit = {
      if (retired.compareAndSet(false, true) && users.get() == 0) {
        closeNow()
      }
    }

    private def closeNow(): Unit = {
      if (!closed.compareAndSet(false, true)) return
      val failure = StrategyDispatcher.closeInstances(acquired.get())
      if (failure != null) throw failure
    }
  }

  private final class StrategyGraph(val scope: CloseScope,
                                    val strategies: ConcurrentHashMap[String, Strategy[T]],
                                    val ordered: util.List[Strategy[T]])
}

/**
  * 策略配置或扩展加载失败。message 含策略名、配置路径和来源，cause 是原始异常。
  */
class StrategyLoadException(val strategyName: String, val configPath: String, message: String, cause: Throwable)
  extends RuntimeException(message, cause)

private case class ParsedStrategy(name: String,
                                  strategyClass: String,
                                  processors: Array[ParsedComponent],
                                  compositors: Array[ParsedComponent],
                                  refs: Array[String],
                                  configParams: JMap[Any, Any])

private case class ParsedComponent(className: String, params: JList[JMap[Any, Any]], typeFilters: JList[String])

private case class ParsedConfig(enabled: Array[ParsedStrategy], topo: Array[ParsedStrategy], declaration: Array[String])

private case class CreatedStrategy[T](strategy: Strategy[T], acquired: Array[AnyRef], acquiredList: util.List[AnyRef])

trait ShortNameMapping {
  def forName(shortName: String): String
}

object StrategyDispatcher {

  private val INSTANTIATION_LOCK = new Object()
  var throwsException = true

  @transient private val lastInstantiatedContext = new AtomicReference[StrategyDispatcher[Any]]()

  def getOrCreate(configFile: String, settings: Settings, shortNameMapping: ShortNameMapping): StrategyDispatcher[Any] = {
    instantiateDefault(configFile, settings, shortNameMapping, useBuilderSettings = false)
  }

  def getOrCreate(configFile: String, settings: Settings): StrategyDispatcher[Any] = {
    instantiateDefault(configFile, settings, null, useBuilderSettings = false)
  }

  def getOrCreate(configFile: String, shortNameMapping: ShortNameMapping): StrategyDispatcher[Any] = {
    instantiateDefault(configFile, null, shortNameMapping, useBuilderSettings = true)
  }

  def getOrCreate(configFile: String): StrategyDispatcher[Any] = {
    instantiateDefault(configFile, null, null, useBuilderSettings = true)
  }

  private def instantiateDefault(configFile: String,
                                 settings: Settings,
                                 shortNameMapping: ShortNameMapping,
                                 useBuilderSettings: Boolean): StrategyDispatcher[Any] = {
    INSTANTIATION_LOCK.synchronized {
      if (lastInstantiatedContext.get() == null) {
        val resolvedSettings = if (useBuilderSettings) settingsBuilder.build() else settings
        val temp = new StrategyDispatcher[Any](resolvedSettings)
        try {
          if (shortNameMapping != null) {
            temp.configShortNameMapping(shortNameMapping)
          }
          temp.loadConfig(configFile)
          setLastInstantiatedContext(temp)
        } catch {
          case t: Throwable =>
            try {
              temp.close()
            } catch {
              case closeError: Throwable if closeError ne t =>
                t.addSuppressed(closeError)
            }
            throw t
        }
      }
      lastInstantiatedContext.get()
    }
  }

  def clear: Unit = {
    INSTANTIATION_LOCK.synchronized {
      val current = lastInstantiatedContext.getAndSet(null)
      if (current != null) current.close()
    }
  }

  private def setLastInstantiatedContext(strategyDispatcher: StrategyDispatcher[Any]): Unit = {
    INSTANTIATION_LOCK.synchronized {
      lastInstantiatedContext.set(strategyDispatcher)
    }
  }

  private[dispatcher] def closeInstances(instances: Array[AnyRef]): Throwable = {
    if (instances == null || instances.length == 0) return null
    val seen = java.util.Collections.newSetFromMap(new util.IdentityHashMap[AnyRef, java.lang.Boolean]())
    var primary: Throwable = null
    var index = instances.length - 1
    while (index >= 0) {
      val instance = instances(index)
      index -= 1
      if (instance != null && seen.add(instance)) {
        try {
          stopInstance(instance)
        } catch {
          case t: Throwable =>
            if (primary == null) primary = t
            else primary.addSuppressed(t)
        }
      }
    }
    primary
  }

  private def stopInstance(instance: AnyRef): Unit = {
    instance match {
      case strategy: Strategy[_] => strategy.stop
      case compositor: Compositor[_] => compositor.stop
      case processor: Processor[_] => processor.stop
      case _ =>
    }
  }
}
