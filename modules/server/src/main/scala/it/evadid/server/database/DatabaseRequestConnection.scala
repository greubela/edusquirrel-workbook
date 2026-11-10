package it.evadid.server.database

import it.evadid.sql.{DatabaseConfiguration, DatabaseConfig, DatabaseConfigWithCredentials}
import it.evadid.distribution.commandTypes.SQLCommands.DbRequest
import it.evadid.server.commandHandler.sql.RichDatabaseConfig
import java.sql.Connection
import scala.util.{Try, Using}

object DatabaseRequestConnection {
  def resolve(config: DatabaseConfiguration, defaults: => DatabaseConfigWithCredentials): DatabaseConfigWithCredentials = {
    require(config.dbFormat == "mysql", "Only MySQL database connections are supported")
    require(Option(config.host).exists(host => host.nonEmpty && !host.exists(c => c.isWhitespace || "/?@#&;\\".contains(c))), "Invalid database host")
    require(Try(config.port.toInt).toOption.exists(port => port > 0 && port <= 65535), "Invalid database port")
    require(Option(config.database).exists(_.matches("[A-Za-z0-9_$-]+")), "Invalid database name")
    config match {
      case provided: DatabaseConfigWithCredentials => provided
      case public: DatabaseConfig =>
        val configured = defaults
        require(public.dbFormat == configured.dbFormat && public.host.equalsIgnoreCase(configured.host) &&
          public.port == configured.port && public.database == configured.database,
          "This database requires user-supplied credentials")
        configured
    }
  }

  def use[A](request: DbRequest)(run: Connection => A): A = {
    val configured = resolve(request.databaseConfig, DatabaseConfigWithCredentials.readFromEnv())
    Using.resource(configured.newConnection()) { connection =>
      // Classic snapshot queries support the existing tables without imposing the
      // exact DDL of the new creation scripts (collation, nullability, precision).
      if request.databaseFormat == it.evadid.sql.DatabaseHistoryFormat.RelationalEvents then {
        val report = EnsureDatabaseProperlySetup.inspect(connection, request.databaseFormat.tables.toSet)
        require(report.valid, s"History database schema is incompatible: missing=${report.missingTables.mkString(",")}; conflicts=${report.conflicts.mkString("; ")}")
      }
      run(connection)
    }
  }
}
