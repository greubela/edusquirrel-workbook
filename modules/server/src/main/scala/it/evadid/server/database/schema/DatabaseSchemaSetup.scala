package it.evadid.server.database.schema

import it.evadid.server.database.SqlStatements
import it.evadid.util.logging.Logger
import java.sql.Connection
import scala.util.{Try, Using}

/** Explicit setup only: refuses nonempty databases and never alters existing structures or rows. */
object DatabaseSchemaSetup {
  import DatabaseSchemaInspector.{Report, inspect}

  /** No ALTER/DROP/UPDATE, and no CREATE at all when any table/view already exists. */
  def setupIfEmpty(connection: Connection, logger: Logger): Report = {
    // MySQL DDL commits implicitly. A connection-scoped advisory lock coordinates
    // simultaneous initializers without using a table or touching application rows.
    val statements = SqlStatements(connection, logger)
    def lockResult(sql: String): Option[Long] = statements.query(sql, "edusquirrel-schema:")(_.getLong(1)).headOption
    require(lockResult("SELECT GET_LOCK(SHA2(CONCAT(?, DATABASE()), 256), 5)").contains(1L), "Another database initializer is running")
    try {
      val before = inspect(connection)
      if before.existingTables.nonEmpty || before.existingRoutines.nonEmpty || before.conflicts.nonEmpty then before
      else {
        require(connection.getAutoCommit, "Database setup requires a dedicated auto-commit connection")
        // Parse and validate every script statement before executing the first one. The DDL comes from the
        // packaged script, never from request data.
        val ddl = DatabaseSqlScripts.statements.map(_.toString)
        Using.resource(connection.createStatement()) { statement => ddl.foreach(statement.executeUpdate) }
        inspect(connection).copy(created = true)
      }
    } finally {
      // A disconnected session also releases its lock; keep the original failure.
      Try(lockResult("SELECT RELEASE_LOCK(SHA2(CONCAT(?, DATABASE()), 256))"))
    }
  }
}
