package net.csdn.jpa

import io.getquill.{Literal, MysqlJdbcContext, SnakeCase}
import net.csdn.jpa.model.JPABase

/**
 * 21/11/2019 WilliamZhu(allwefantasy@gmail.com)
 */
object QuillDB {
  def createDataSource: javax.sql.DataSource with java.io.Closeable = {
    JPABase.mysqlClient.defaultMysqlService().dataSource().asInstanceOf[javax.sql.DataSource with java.io.Closeable]
  }

  lazy val ctx = new MysqlJdbcContext(SnakeCase, createDataSource)
}
