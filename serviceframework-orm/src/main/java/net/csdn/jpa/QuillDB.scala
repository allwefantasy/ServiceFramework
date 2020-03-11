package net.csdn.jpa

import java.io.{ByteArrayInputStream, InputStreamReader}
import java.util

import io.getquill.{MysqlJdbcContext, SnakeCase}
import net.csdn.common.io.Streams
import net.csdn.common.settings.ImmutableSettings
import net.csdn.common.settings.ImmutableSettings.YamlSettingsLoader
import net.csdn.jpa.model.JPABase
import net.csdn.modules.persist.mysql.DataSourceManager

/**
 * 21/11/2019 WilliamZhu(allwefantasy@gmail.com)
 */
object QuillDB {
  val cache = new java.util.concurrent.ConcurrentHashMap[String, MysqlJdbcContext[SnakeCase.type]]()
  val cacheDataSource = new java.util.concurrent.ConcurrentHashMap[String, javax.sql.DataSource with java.io.Closeable]()

  def createDataSource: javax.sql.DataSource with java.io.Closeable = {
    if (JPABase.mysqlClient.defaultMysqlService() != null) {
      JPABase.mysqlClient.defaultMysqlService().dataSource().
        asInstanceOf[javax.sql.DataSource with java.io.Closeable]
    } else {
      null
    }

  }

  lazy val ctx = new MysqlJdbcContext(SnakeCase, createDataSource)

  def createNewCtxByNameFromYml(name: String): MysqlJdbcContext[SnakeCase.type] = {
    if (QuillDB.cache.containsKey(name)) {
      return QuillDB.cache.get(name)
    }
    synchronized {
      def createDataSource: javax.sql.DataSource with java.io.Closeable = {
        if (JPABase.mysqlClient.defaultMysqlService() != null) {
          JPABase.mysqlClient.mysqlService(name).dataSource().
            asInstanceOf[javax.sql.DataSource with java.io.Closeable]
        } else {
          cacheDataSource.get(name)
        }

      }

      val tmp = new MysqlJdbcContext(SnakeCase, createDataSource)
      QuillDB.cache.put(name, tmp)
      tmp
    }
  }

  def createNewCtxByNameFromStr(name: String, snippet: String): MysqlJdbcContext[SnakeCase.type] = {
    val loadedSettings: util.Map[String, String] = YamlSettingsLoader.load(Streams.copyToString(new InputStreamReader(new ByteArrayInputStream(snippet.getBytes("utf-8")), "UTF-8")))
    val settingBuilder = ImmutableSettings.settingsBuilder()
    settingBuilder.put(loadedSettings)
    val dbSettings = settingBuilder.build()
    if (QuillDB.cache.containsKey(name)) {
      return QuillDB.cache.get(name)
    }
    if (JPABase.mysqlClient.defaultMysqlService() != null) {
      JPABase.mysqlClient.defaultMysqlService().addNewMySQL(name, dbSettings.getByPrefix(name + "."))
    } else {
      val dataSourceManager = new DataSourceManager(ImmutableSettings.settingsBuilder().build())
      val ds = dataSourceManager.buildPool(dbSettings.getByPrefix(name + "."));
      cacheDataSource.put(name, ds.asInstanceOf[javax.sql.DataSource with java.io.Closeable])
    }

    createNewCtxByNameFromYml(name)
  }
}
