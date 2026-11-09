package it.evadid.server.database

import it.evadid.sql.DatabaseConfigWithCredentials
import it.evadid.server.commandHandler.sql.RichDatabaseConfig
import java.sql.Connection
import scala.util.Using

object MysqlTestDatabase {
  def withConnection[A](run: Connection => A): A = {
    require(sys.env.get("SQL_DATABASE").contains("edusquirrel_account_test"), "Use only the disposable edusquirrel_account_test database")
    require(sys.env.get("SQL_HOST").exists(Set("127.0.0.1", "localhost")), "Use only a localhost test database")
    Using.resource(DatabaseConfigWithCredentials.readFromEnv().newConnection())(run)
  }

  def clearSchema(connection: Connection): Unit = {
    require(connection.getCatalog == "edusquirrel_account_test")
    Using.resource(connection.createStatement()) { statement =>
      val tables = Using.resource(connection.getMetaData.getTables(connection.getCatalog, null, "%", Array("TABLE"))) { rows =>
        Iterator.continually(rows.next()).takeWhile(identity).map(_ => rows.getString("TABLE_NAME")).toList
      }
      statement.execute("SET FOREIGN_KEY_CHECKS=0")
      try tables.foreach { name =>
        require(name.matches("[A-Za-z_][A-Za-z0-9_]*"))
        statement.executeUpdate(s"DROP TABLE `$name`")
      } finally statement.execute("SET FOREIGN_KEY_CHECKS=1")
    }
  }

  def reset(connection: Connection): Unit = {
    clearSchema(connection)
    val report = EnsureDatabaseProperlySetup.setupIfEmpty(connection)
    require(report.valid, report.toString)
  }
}
