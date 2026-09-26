package net.csdn.jpa

import java.io.{ByteArrayInputStream, InputStreamReader}
import java.util

import io.getquill.context.jdbc.JdbcContext
import io.getquill.{MysqlJdbcContext, PostgresJdbcContext, SnakeCase}
import net.csdn.common.enhancer.{EnhancementContext, EnhancementFailure}
import net.csdn.common.io.Streams
import net.csdn.common.settings.{ImmutableSettings, JdbcEngine, Settings}
import net.csdn.common.settings.ImmutableSettings.YamlSettingsLoader
import net.csdn.modules.persist.mysql.{DataSourceManager, SharedDataSource}

/**
 * Quill contexts belong to one OrmSession.
 *
 * `ctx` and `postgresCtx` are methods so two applications do not share a
 * process-wide context. Scala rejects `import QuillDB.ctx._` because a method
 * is not a stable path. The supported form is `val ctx = QuillDB.ctx` and then
 * `import ctx._` (or the equivalent `postgresCtx`).
 *
 * `createNewCtxByNameFromStr` keeps the original MySQL spelling. PostgreSQL
 * callers use `createNewPostgresCtxByNameFromStr`; both forms bind the pool to
 * the same owner lifecycle and never reuse another application's pool.
 */
object QuillDB {

  def defaultDBEnable: Boolean = JPA.isConfigured

  private val compatLock = new Object
  private var compatContext: EnhancementContext = _

  private def resources(session: OrmSession): QuillResources = {
    session.synchronized {
      val existing = session.quillState()
      if (existing != null) {
        existing.asInstanceOf[QuillResources]
      } else {
        val created = new QuillResources
        try {
          session.bindQuill(created, created)
        } catch {
          case thrown: RuntimeException =>
            try {
              created.close()
            } catch {
              case closeFailure: RuntimeException => thrown.addSuppressed(closeFailure)
            }
            throw thrown
        }
        val bound = session.quillState().asInstanceOf[QuillResources]
        if (!(bound eq created)) {
          created.close()
        }
        bound
      }
    }
  }

  def createDataSource: javax.sql.DataSource with java.io.Closeable = sharedDefault(OrmSession.current())

  def ctx: MysqlJdbcContext[SnakeCase.type] = {
    defaultCtx(JdbcEngine.MYSQL).asInstanceOf[MysqlJdbcContext[SnakeCase.type]]
  }

  def postgresCtx: PostgresJdbcContext[SnakeCase.type] = {
    defaultCtx(JdbcEngine.POSTGRES).asInstanceOf[PostgresJdbcContext[SnakeCase.type]]
  }

  def createNewCtxByNameFromYml(name: String): MysqlJdbcContext[SnakeCase.type] = {
    namedFromYml(name, JdbcEngine.MYSQL).asInstanceOf[MysqlJdbcContext[SnakeCase.type]]
  }

  def createNewPostgresCtxByNameFromYml(name: String): PostgresJdbcContext[SnakeCase.type] = {
    namedFromYml(name, JdbcEngine.POSTGRES).asInstanceOf[PostgresJdbcContext[SnakeCase.type]]
  }

  def createNewCtxByNameFromStr(name: String, snippet: String): MysqlJdbcContext[SnakeCase.type] = {
    namedFromSnippet(name, snippet, JdbcEngine.MYSQL).asInstanceOf[MysqlJdbcContext[SnakeCase.type]]
  }

  def createNewPostgresCtxByNameFromStr(name: String, snippet: String): PostgresJdbcContext[SnakeCase.type] = {
    namedFromSnippet(name, snippet, JdbcEngine.POSTGRES).asInstanceOf[PostgresJdbcContext[SnakeCase.type]]
  }

  private def defaultCtx(engine: String): JdbcContext[_, SnakeCase.type] = {
    val session = OrmSession.current()
    val source = sharedDefault(session, engine)
    if (source == null) {
      throw missing(engine, "default")
    }
    resources(session).defaultCtx(engine, source)
  }

  private def namedFromYml(name: String, engine: String): JdbcContext[_, SnakeCase.type] = {
    val session = namedOwner()
    val existing = session.quillState()
    if (existing != null) {
      val cached = existing.asInstanceOf[QuillResources].cached(engine, name)
      if (cached != null) {
        return cached
      }
    }
    val source = sourceFor(session, name, engine)
    resources(session).named(engine, name, source)
  }

  private def namedFromSnippet(name: String, snippet: String, engine: String): JdbcContext[_, SnakeCase.type] = {
    val loadedSettings: util.Map[String, String] = YamlSettingsLoader.load(
      Streams.copyToString(new InputStreamReader(new ByteArrayInputStream(snippet.getBytes("utf-8")), "UTF-8")))
    val settingBuilder = ImmutableSettings.settingsBuilder()
    settingBuilder.put(loadedSettings)
    val dbSettings = settingBuilder.build()
    val prefix = dbSettings.getByPrefix(name + ".")
    val session = snippetOwner()
    val existing = session.quillState()
    if (existing != null) {
      val cached = existing.asInstanceOf[QuillResources].cached(engine, name)
      if (cached != null) {
        return cached
      }
    }
    if (session.hasConfiguration() && session.datasourceAvailable()) {
      session.sqlClient().addNewDatasource(name, prefix, engine)
    } else {
      val opened = resources(session)
      val manager = DataSourceManager.standalone(ImmutableSettings.settingsBuilder().build())
      try {
        val pool = manager.buildPool(prefix, engine)
        opened.own(engine, name, manager, pool)
      } catch {
        case thrown: RuntimeException =>
          try {
            manager.close()
          } catch {
            case closeFailure: RuntimeException => thrown.addSuppressed(closeFailure)
          }
          throw thrown
      }
    }
    val source = sourceFor(session, name, engine)
    resources(session).named(engine, name, source)
  }

  /**
   * Closes the no-scope compat owner and its pools. Does not close an active
   * application or the default JPA context. The closed pool is not returned again.
   */
  def close(): Unit = {
    val current = compatLock.synchronized {
      val found = compatContext
      compatContext = null
      found
    }
    if (current != null && !current.isClosed) {
      current.close()
    }
  }

  private def snippetOwner(): OrmSession = {
    val active = EnhancementContext.currentOrNull()
    if (active != null) {
      if (active.isClosed) {
        throw lifecycle("the active enhancement context is closed")
      }
      return OrmSession.attach(active)
    }
    val existing = OrmSession.currentOrNull()
    if (existing != null) {
      return existing
    }
    compatOwner()
  }

  private def namedOwner(): OrmSession = {
    val active = EnhancementContext.currentOrNull()
    if (active != null) {
      return OrmSession.current()
    }
    val existing = OrmSession.currentOrNull()
    if (existing != null) {
      return existing
    }
    compatLock.synchronized {
      if (compatContext != null && !compatContext.isClosed) {
        OrmSession.attach(compatContext)
      } else {
        throw lifecycle("JPA is not configured")
      }
    }
  }

  private def compatOwner(): OrmSession = {
    compatLock.synchronized {
      if (compatContext == null || compatContext.isClosed) {
        compatContext = EnhancementContext.open(classOf[QuillResources].getClassLoader)
      }
      OrmSession.attach(compatContext)
    }
  }

  private def sharedDefault(session: OrmSession): SharedDataSource = {
    val engine = primaryEngine(session)
    sharedDefault(session, engine)
  }

  private def sharedDefault(session: OrmSession, engine: String): SharedDataSource = {
    requirePrimary(session, engine)
    if (!session.datasourceAvailable()) {
      return null
    }
    val service = session.sqlClient().defaultMysqlService()
    if (service == null || service.dataSource() == null) {
      null
    } else {
      new SharedDataSource(service.dataSource())
    }
  }

  private def sourceFor(session: OrmSession, name: String, engine: String): SharedDataSource = {
    if (session.datasourceAvailable()) {
      val client = session.sqlClient()
      val service = client.mysqlService(name)
      if (service == null || service.dataSource() == null) {
        return null
      }
      val actual = client.engineFor(name)
      if (actual == null) {
        throw untracked(name)
      }
      if (JdbcEngine.normalize(actual) != JdbcEngine.normalize(engine)) {
        throw wrongEngine(engine, name, actual)
      }
      return new SharedDataSource(service.dataSource())
    }
    val state = session.quillState()
    if (state == null) {
      return null
    }
    val owned = state.asInstanceOf[QuillResources].ownedSource(engine, name)
    if (owned == null) {
      null
    } else {
      new SharedDataSource(owned)
    }
  }

  private def primaryEngine(session: OrmSession): String = {
    if (!session.hasConfiguration()) {
      return JdbcEngine.MYSQL
    }
    JdbcEngine.primary(session.configuration().getSettings, session.configuration().getMode).engine()
  }

  private def requirePrimary(session: OrmSession, engine: String): Unit = {
    val actual = primaryEngine(session)
    if (JdbcEngine.normalize(engine) != actual) {
      throw new EnhancementFailure(
        EnhancementFailure.Category.CONFIGURATION,
        null,
        null,
        "quill",
        JdbcEngine.normalize(engine) + " Quill API requires datasources.primary=" + JdbcEngine.normalize(engine)
          + "; actual primary is " + String.valueOf(actual),
        null)
    }
  }

  private def lifecycle(detail: String): EnhancementFailure = {
    new EnhancementFailure(
      EnhancementFailure.Category.LIFECYCLE,
      null,
      null,
      "quill",
      detail,
      null)
  }

  private def missing(engine: String, name: String): EnhancementFailure = {
    new EnhancementFailure(
      EnhancementFailure.Category.CONFIGURATION,
      null,
      null,
      "quill",
      JdbcEngine.normalize(engine) + " datasource was not found: " + name,
      null)
  }

  private def wrongEngine(engine: String, name: String, actual: String): EnhancementFailure = {
    new EnhancementFailure(
      EnhancementFailure.Category.CONFIGURATION,
      null,
      null,
      "quill",
      "datasource engine mismatch for " + name + ": expected "
        + JdbcEngine.normalize(engine) + ", actual " + JdbcEngine.normalize(actual),
      null)
  }

  private def untracked(name: String): EnhancementFailure = {
    new EnhancementFailure(
      EnhancementFailure.Category.CONFIGURATION,
      null,
      null,
      "quill",
      "datasource " + name + " has no recorded engine",
      null)
  }
}

final class QuillResources extends java.io.Closeable {
  private val contexts = new java.util.ArrayList[JdbcContext[_, SnakeCase.type]]()
  private val cache = new java.util.LinkedHashMap[String, JdbcContext[_, SnakeCase.type]]()
  private val ownedManagers = new java.util.ArrayList[DataSourceManager]()
  private val ownedSources = new java.util.LinkedHashMap[String, javax.sql.DataSource]()
  private var defaultContexts = new java.util.LinkedHashMap[String, JdbcContext[_, SnakeCase.type]]()
  private var closed = false

  private def cacheKey(engine: String, name: String): String = JdbcEngine.normalize(engine) + "\u0000" + name

  def cached(engine: String, name: String): JdbcContext[_, SnakeCase.type] = synchronized {
    cache.get(cacheKey(engine, name))
  }

  def ownedSource(engine: String, name: String): javax.sql.DataSource = synchronized {
    ownedSources.get(cacheKey(engine, name))
  }

  def own(engine: String, name: String, manager: DataSourceManager, pool: javax.sql.DataSource): Unit = synchronized {
    ensureOpen()
    val key = cacheKey(engine, name)
    if (ownedSources.containsKey(key)) {
      throw new EnhancementFailure(
        EnhancementFailure.Category.CONFLICT,
        null,
        null,
        "quill",
        "datasource name is already registered: " + name,
        null)
    }
    ownedManagers.add(manager)
    ownedSources.put(key, pool)
  }

  def defaultCtx(engine: String, source: javax.sql.DataSource with java.io.Closeable): JdbcContext[_, SnakeCase.type] = synchronized {
    ensureOpen()
    val key = JdbcEngine.normalize(engine)
    val found = defaultContexts.get(key)
    if (found != null) {
      found
    } else {
      val created = open(key, source, "default")
      defaultContexts.put(key, created)
      created
    }
  }

  def named(engine: String, name: String, source: javax.sql.DataSource with java.io.Closeable): JdbcContext[_, SnakeCase.type] = synchronized {
    ensureOpen()
    val key = cacheKey(engine, name)
    val found = cache.get(key)
    if (found != null) {
      found
    } else {
      val created = open(engine, source, name)
      cache.put(key, created)
      created
    }
  }

  private def open(engine: String, source: javax.sql.DataSource with java.io.Closeable, name: String): JdbcContext[_, SnakeCase.type] = {
    if (source == null) {
      throw new EnhancementFailure(
        EnhancementFailure.Category.CONFIGURATION,
        null,
        null,
        "quill",
        JdbcEngine.normalize(engine) + " datasource was not found: " + name,
        null)
    }
    val created = if (JdbcEngine.POSTGRES == JdbcEngine.normalize(engine)) {
      new PostgresJdbcContext(SnakeCase, source)
    } else {
      new MysqlJdbcContext(SnakeCase, source)
    }
    contexts.add(created)
    created
  }

  override def close(): Unit = synchronized {
    if (closed) {
      return
    }
    closed = true
    var primary: Throwable = null
    def keep(thrown: Throwable): Unit = {
      if (primary == null) {
        primary = thrown
      } else {
        primary.addSuppressed(thrown)
      }
    }
    var index = contexts.size() - 1
    while (index >= 0) {
      try {
        contexts.get(index).close()
      } catch {
        case thrown: Throwable => keep(thrown)
      }
      index -= 1
    }
    contexts.clear()
    cache.clear()
    defaultContexts.clear()
    index = ownedManagers.size() - 1
    while (index >= 0) {
      try {
        ownedManagers.get(index).close()
      } catch {
        case thrown: Throwable => keep(thrown)
      }
      index -= 1
    }
    ownedManagers.clear()
    ownedSources.clear()
    if (primary != null) {
      primary match {
        case runtime: RuntimeException => throw runtime
        case error: Error => throw error
        case other => throw new RuntimeException(other)
      }
    }
  }

  private def ensureOpen(): Unit = {
    if (closed) {
      throw new EnhancementFailure(
        EnhancementFailure.Category.LIFECYCLE,
        null,
        null,
        "quill",
        "quill resources are closed",
        null)
    }
  }
}
