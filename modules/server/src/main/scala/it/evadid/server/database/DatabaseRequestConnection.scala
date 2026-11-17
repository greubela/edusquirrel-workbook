package it.evadid.server.database

import it.evadid.sql.{BackendDatabase, DatabaseConnection, DatabaseConfiguration, DatabaseConfig, DatabaseConfigWithCredentials}
import it.evadid.distribution.commandTypes.SQLCommands.DbRequest
import it.evadid.util.logging.Logger
import it.evadid.server.commandHandler.sql.RichDatabaseConfig
import java.sql.Connection
import scala.util.{Try, Using}

object DatabaseRequestConnection {
  def resolve(config: DatabaseConfiguration, defaults: => DatabaseConfigWithCredentials): DatabaseConfigWithCredentials = {
    def validate(connection: DatabaseConnection): Unit = {
      require(connection.dbFormat == "mysql", "Only MySQL database connections are supported")
      require(Option(connection.host).exists(host => host.nonEmpty && !host.exists(c => c.isWhitespace || "/?@#&;\\".contains(c))), "Invalid database host")
      require(Try(connection.port.toInt).toOption.exists(port => port > 0 && port <= 65535), "Invalid database port")
      require(Option(connection.database).exists(_.matches("[A-Za-z0-9_$-]+")), "Invalid database name")
    }
    val resolved = config match {
      case BackendDatabase => defaults
      case provided: DatabaseConfigWithCredentials => provided
      case public: DatabaseConfig =>
        validate(public)
        val configured = defaults
        require(public == configured.withoutCredentials, "This database requires user-supplied credentials")
        configured
    }
    validate(resolved)
    resolved
  }

  def use[A](request: DbRequest, logger: Logger)(run: Connection => A): A = SqlFailure(s"executing ${request.databaseFormat} history request", logger) {
    val configured = resolve(request.databaseConfig, DatabaseConfigWithCredentials.readFromEnv())
    Using.resource(configured.newConnection()) { connection =>
      val report = EnsureDatabaseProperlySetup.inspect(connection, request.databaseFormat.tables.map(_.name).toSet)
      require(report.valid, s"History database schema is incompatible: missing=${report.missingTables.mkString(",")}; conflicts=${report.conflicts.mkString("; ")}")
      run(connection)
    }
  }
}
