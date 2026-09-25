package net.csdn.jpa

import java.io.{ByteArrayInputStream, InputStreamReader}
import java.util

import io.getquill.{MysqlJdbcContext, SnakeCase}
import net.csdn.common.enhancer.{EnhancementContext, EnhancementFailure}
import net.csdn.common.io.Streams
import net.csdn.common.settings.ImmutableSettings
import net.csdn.common.settings.ImmutableSettings.YamlSettingsLoader
import net.csdn.modules.persist.mysql.{DataSourceManager, SharedDataSource}

/**
 * Quill contexts belong to one OrmSession.
 *
 * `ctx` is a method so two applications do not share a process-wide context.
 * Scala rejects `import QuillDB.ctx._` because a method is not a stable path.
 * The supported form is `val ctx = QuillDB.ctx` and then `import ctx._`.
 *
 * `createNewCtxByNameFromStr` still opens a pool from a snippet when the
 * current enhancement context has no JPA entities. That pool closes with the
 * context and is not taken from another application. With no scope and no ORM
 * session, the pool is owned by a compat context. `close` drops that owner.
 * A later call creates a new pool and does not return the closed one.
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
    val session = OrmSession.current()
    val source = sharedDefault(session)
    if (source == null) {
      throw missing("default")
    }
    resources(session).defaultCtx(source)
  }

  def createNewCtxByNameFromYml(name: String): MysqlJdbcContext[SnakeCase.type] = {
    val session = namedOwner()
    val existing = session.quillState()
    if (existing != null) {
      val cached = existing.asInstanceOf[QuillResources].cached(name)
      if (cached != null) {
        return cached
      }
    }
    val source = sourceFor(session, name)
    resources(session).named(name, source)
  }

  def createNewCtxByNameFromStr(name: String, snippet: String): MysqlJdbcContext[SnakeCase.type] = {
    val loadedSettings: util.Map[String, String] = YamlSettingsLoader.load(
      Streams.copyToString(new InputStreamReader(new ByteArrayInputStream(snippet.getBytes("utf-8")), "UTF-8")))
    val settingBuilder = ImmutableSettings.settingsBuilder()
    settingBuilder.put(loadedSettings)
    val dbSettings = settingBuilder.build()
    val prefix = dbSettings.getByPrefix(name + ".")
    val session = snippetOwner()
    val existing = session.quillState()
    if (existing != null) {
      val cached = existing.asInstanceOf[QuillResources].cached(name)
      if (cached != null) {
        return cached
      }
    }
    if (session.mysqlAvailable()) {
      val service = session.mysqlClient().defaultMysqlService()
      if (service == null) {
        throw missing(name)
      }
      service.addNewMySQL(name, prefix)
    } else {
      val opened = resources(session)
      val manager = DataSourceManager.standalone(ImmutableSettings.settingsBuilder().build())
      try {
        val pool = manager.buildPool(prefix)
        opened.own(name, manager, pool)
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
    val source = sourceFor(session, name)
    resources(session).named(name, source)
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
    if (!session.mysqlAvailable()) {
      return null
    }
    val service = session.mysqlClient().defaultMysqlService()
    if (service == null || service.dataSource() == null) {
      null
    } else {
      new SharedDataSource(service.dataSource())
    }
  }

  private def sourceFor(session: OrmSession, name: String): SharedDataSource = {
    if (session.mysqlAvailable()) {
      val service = session.mysqlClient().mysqlService(name)
      if (service == null || service.dataSource() == null) {
        return null
      }
      return new SharedDataSource(service.dataSource())
    }
    val state = session.quillState()
    if (state == null) {
      return null
    }
    val owned = state.asInstanceOf[QuillResources].ownedSource(name)
    if (owned == null) {
      null
    } else {
      new SharedDataSource(owned)
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

  private def missing(name: String): EnhancementFailure = {
    new EnhancementFailure(
      EnhancementFailure.Category.CONFIGURATION,
      null,
      null,
      "quill",
      "mysql datasource was not found: " + name,
      null)
  }
}

final class QuillResources extends java.io.Closeable {
  private val contexts = new java.util.ArrayList[MysqlJdbcContext[SnakeCase.type]]()
  private val cache = new java.util.LinkedHashMap[String, MysqlJdbcContext[SnakeCase.type]]()
  private val ownedManagers = new java.util.ArrayList[DataSourceManager]()
  private val ownedSources = new java.util.LinkedHashMap[String, javax.sql.DataSource]()
  private var defaultContext: MysqlJdbcContext[SnakeCase.type] = _
  private var closed = false

  def cached(name: String): MysqlJdbcContext[SnakeCase.type] = synchronized {
    cache.get(name)
  }

  def ownedSource(name: String): javax.sql.DataSource = synchronized {
    ownedSources.get(name)
  }

  def own(name: String, manager: DataSourceManager, pool: javax.sql.DataSource): Unit = synchronized {
    ensureOpen()
    ownedManagers.add(manager)
    ownedSources.put(name, pool)
  }

  def defaultCtx(source: javax.sql.DataSource with java.io.Closeable): MysqlJdbcContext[SnakeCase.type] = synchronized {
    ensureOpen()
    if (defaultContext == null) {
      defaultContext = open(source, "default")
    }
    defaultContext
  }

  def named(name: String, source: javax.sql.DataSource with java.io.Closeable): MysqlJdbcContext[SnakeCase.type] = synchronized {
    ensureOpen()
    val found = cache.get(name)
    if (found != null) {
      found
    } else {
      val created = open(source, name)
      cache.put(name, created)
      created
    }
  }

  private def open(source: javax.sql.DataSource with java.io.Closeable, name: String): MysqlJdbcContext[SnakeCase.type] = {
    if (source == null) {
      throw new EnhancementFailure(
        EnhancementFailure.Category.CONFIGURATION,
        null,
        null,
        "quill",
        "mysql datasource was not found: " + name,
        null)
    }
    val created = new MysqlJdbcContext(SnakeCase, source)
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
    defaultContext = null
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
