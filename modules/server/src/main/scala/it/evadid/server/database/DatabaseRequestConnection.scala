package it.evadid.server.database

import it.evadid.sql.{DatabaseConfiguration, DatabaseConfig, DatabaseConfigWithCredentials}
import it.evadid.distribution.commandTypes.SQLCommands.DbRequest
import it.evadid.server.commandHandler.sql.RichDatabaseConfig
import it.evadid.util.logging.Logger
import java.sql.{Connection, SQLException}
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

  def use[A](request: DbRequest, logger: Logger)(run: Connection => A): A = try {
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
  } catch {
    case error: SQLException =>
      logger.logExceptionWarn("History database request failed", error)
      val reason = Option(error.getSQLState).map(_.take(2)) match {
        case Some("08") => "Could not connect to or communicate with the database. Check its availability and connection settings."
        case Some("28") => "The database rejected the credentials. Check the configured database user and password."
        case Some("23") => "A database constraint rejected the history. Check the selected history schema and answer references."
        case Some("22") => "The history contains data outside the database column limits or supported values."
        case Some("40") => "The database transaction conflicted with another operation. Retry the sync."
        case Some("42") => "The database rejected the query. Check the history schema and database permissions."
        case _ => "The database could not complete the history operation. Check the server's database logs."
      }
      // Keep vendor details in server logs; SQL messages can contain answer data or credentials.
      throw new SQLException(s"$reason (SQLState=${Option(error.getSQLState).getOrElse("unknown")}, errorCode=${error.getErrorCode})",
        error.getSQLState, error.getErrorCode)
  }
}
