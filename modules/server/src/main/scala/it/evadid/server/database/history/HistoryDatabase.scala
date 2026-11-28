package it.evadid.server.database.history

import it.evadid.distribution.commandTypes.SQLCommands.DbRequest
import it.evadid.server.database.Database
import it.evadid.server.database.schema.SchemaValidation
import it.evadid.sql.{BackendDatabase, DatabaseConfig, DatabaseConfigWithCredentials, DatabaseConfiguration, DatabaseConnection, DatabaseHistoryFormat}
import it.evadid.util.logging.Logger
import scala.util.Try

/** Runs history requests on the database they name: the backend's own one, or one users bring with credentials. */
final class HistoryDatabase(backend: => DatabaseConfigWithCredentials, schemas: SchemaValidation) {

  /** Opens the request's validated database and runs `operation` on the storage for its history layout. */
  def run[A](request: DbRequest, logger: Logger)(operation: HistorySql => A): A = {
    val configured = HistoryDatabase.resolve(request.databaseConfig, backend)
    Database.withConnection(configured, s"executing ${request.databaseFormat} history request", logger) { connection =>
      schemas.requireValid(SchemaValidation.Requirement(configured, request.databaseFormat.tables.map(_.name).toSet))
      operation(request.databaseFormat match {
        case DatabaseHistoryFormat.RelationalFormat => RelationalHistoryStorage(connection, logger)
        case DatabaseHistoryFormat.BlobFormat => BlobHistoryStorage(connection, logger)
      })
    }
  }
}

object HistoryDatabase {
  /** The backend database for [[BackendDatabase]] or its public address; supplied credentials connect as given. */
  def resolve(config: DatabaseConfiguration, backend: => DatabaseConfigWithCredentials): DatabaseConfigWithCredentials = {
    val resolved = config match {
      case BackendDatabase => backend
      case provided: DatabaseConfigWithCredentials => provided
      case public: DatabaseConfig =>
        validate(public)
        val configured = backend
        require(public == configured.withoutCredentials, "This database requires user-supplied credentials")
        configured
    }
    validate(resolved)
    resolved
  }

  /** Only plain MySQL addresses: no characters that could add JDBC URL parameters or paths. */
  private def validate(connection: DatabaseConnection): Unit = {
    require(connection.dbFormat == "mysql", "Only MySQL database connections are supported")
    require(Option(connection.host).exists(host => host.nonEmpty && !host.exists(c => c.isWhitespace || "/?@#&;\\".contains(c))), "Invalid database host")
    require(Try(connection.port.toInt).toOption.exists(port => port > 0 && port <= 65535), "Invalid database port")
    require(Option(connection.database).exists(_.matches("[A-Za-z0-9_$-]+")), "Invalid database name")
  }
}
