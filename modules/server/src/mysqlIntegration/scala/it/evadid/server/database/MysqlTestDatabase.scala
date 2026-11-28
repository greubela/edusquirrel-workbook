package it.evadid.server.database

import it.evadid.server.ServerConfig
import it.evadid.server.database.schema.DatabaseSchemaSetup
import it.evadid.sql.DatabaseConfigWithCredentials
import it.evadid.util.logging.BasicLogger
import java.sql.Connection
import scala.util.Using

/** The disposable localhost database named by the test environment's `SQL_*` and `SERVER_SECRET` settings. */
object MysqlTestDatabase {
  lazy val serverConfig: ServerConfig = {
    require(sys.env.get("SQL_DATABASE").contains("edusquirrel_account_test"), "Use only the disposable edusquirrel_account_test database")
    require(sys.env.get("SQL_HOST").exists(Set("127.0.0.1", "localhost")), "Use only a localhost test database")
    ServerConfig.fromEnvironment()
  }

  def config: DatabaseConfigWithCredentials = serverConfig.backendDatabase

  def withConnection[A](run: Connection => A): A = Using.resource(Database.open(config))(run)

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
    val report = DatabaseSchemaSetup.setupIfEmpty(connection, BasicLogger())
    require(report.valid, report.toString)
  }
}
