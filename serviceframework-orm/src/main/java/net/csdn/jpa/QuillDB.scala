package net.csdn.jpa

import java.io.{ByteArrayInputStream, InputStreamReader}
import java.util

import io.getquill.{MysqlJdbcContext, SnakeCase}
import net.csdn.common.io.Streams
import net.csdn.common.settings.ImmutableSettings
import net.csdn.common.settings.ImmutableSettings.YamlSettingsLoader
import net.csdn.jpa.model.JPABase

/**
 * 21/11/2019 WilliamZhu(allwefantasy@gmail.com)
 */
object QuillDB {
  val cache = new java.util.concurrent.ConcurrentHashMap[String, MysqlJdbcContext[SnakeCase.type]]()

  def createDataSource: javax.sql.DataSource with java.io.Closeable = {
    JPABase.mysqlClient.defaultMysqlService().dataSource().
      asInstanceOf[javax.sql.DataSource with java.io.Closeable]
  }

  lazy val ctx = new MysqlJdbcContext(SnakeCase, createDataSource)

  def createNewCtxByNameFromYml(name: String): MysqlJdbcContext[SnakeCase.type] = {
    if (QuillDB.cache.containsKey(name)) {
      return QuillDB.cache.get(name)
    }
    synchronized {
      def createDataSource: javax.sql.DataSource with java.io.Closeable = {
        JPABase.mysqlClient.mysqlService(name).dataSource().
          asInstanceOf[javax.sql.DataSource with java.io.Closeable]
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
    JPABase.mysqlClient.defaultMysqlService().addNewMySQL(name, dbSettings.getByPrefix(name + "."))
    createNewCtxByNameFromYml(name)
  }
}
