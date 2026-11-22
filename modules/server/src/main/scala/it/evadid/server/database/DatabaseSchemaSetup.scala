package it.evadid.server.database

import it.evadid.sql.DatabaseConfigWithCredentials
import it.evadid.util.logging.Logger
import java.sql.Connection
import scala.util.{Try, Using}

/** Explicit setup only: refuses nonempty databases and never alters existing structures or rows. */
object DatabaseSchemaSetup {
  import DatabaseSchemaInspector.{Report, inspect}

  /** No ALTER/DROP/UPDATE, and no CREATE at all when any table/view already exists. */
  def setupIfEmpty(connection: Connection): Report = {
    // MySQL DDL commits implicitly. A connection-scoped advisory lock coordinates
    // simultaneous backend startups without using a table or touching application rows.
    val lock = "SHA2(CONCAT('edusquirrel-schema:',DATABASE()),256)"
    def lockQuery(sql: String): Int = Using.resource(connection.createStatement()) { statement =>
      Using.resource(statement.executeQuery(sql)) { result =>
        require(result.next(), "Database lock query returned no result")
        result.getInt(1)
      }
    }
    require(lockQuery(s"SELECT GET_LOCK($lock,5)") == 1, "Another database initializer is running")
    try {
      val before = inspect(connection)
      if before.existingTables.nonEmpty || before.existingRoutines.nonEmpty || before.conflicts.nonEmpty then before
      else {
        require(connection.getAutoCommit, "Database setup requires a dedicated auto-commit connection")
        // Parse and validate every script before executing the first DDL statement.
        val statements = DatabaseSqlScripts.statements.map(_.toString)
        Using.resource(connection.createStatement()) { statement => statements.foreach(statement.executeUpdate) }
        inspect(connection).copy(created = true)
      }
    } finally {
      // A disconnected session also releases its lock; keep the original failure.
      Try(lockQuery(s"SELECT RELEASE_LOCK($lock)"))
    }
  }

  def onStartup(logger: Logger): Report = {
    val report = DatabaseSchemaInspector.withConnection(DatabaseConfigWithCredentials.readFromEnv())(setupIfEmpty)
    if !report.valid then {
      val message = s"Database schema check failed: reachable=${report.reachable}; missing=${report.missingTables.mkString(",")}; conflicts=${report.conflicts.mkString("; ")}"
      logger.logError(message)
      throw IllegalStateException(message)
    }
    report
  }
}
