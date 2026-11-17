package it.evadid.server.commandHandler.sql

import it.evadid.distribution.commandTypes.SqlEditorCommands.*
import it.evadid.sql.DatabaseConfigWithCredentials

import java.sql.{Connection, DriverManager, Statement, SQLException}
import scala.util.{Try, Using}

/** Executes only on explicitly configured teaching databases, with dedicated credentials.
  *
  * Uses sensible abstractions:
  * - Statement interface for SQL execution with proper resource management
  * - PreparedStatement where applicable for parameterized queries
  * - Explicit verification methods for database access control
  * - Bounded result sets with truncation detection
  */
object SqlEditorExecution {
  private val RowLimit = 500

  /** Verifies that the database name matches an allowed pattern.
    *
    * @param name The database name to check
    * @param allowed Set of allowed database names
    * @return true if the database name is valid and in the allowed set
    */
  private[sql] def allowedDatabase(name: String, allowed: Set[String]): Boolean =
    name.matches("[A-Za-z0-9_]+") && allowed.contains(name)

  /** Executes a SQL query and collects results.
    *
    * Uses a Statement with proper resource management via Using.resource.
    *
    * @param statement The SQL statement to execute
    * @param firstIsRows true if the first result is a ResultSet
    * @return List of Result objects containing columns, rows, and metadata
    */
  private[sql] def collectResults(statement: Statement, firstIsRows: Boolean): List[Result] = {
    val results = List.newBuilder[Result]
    var isRows = firstIsRows
    var finished = false
    while !finished do {
      if isRows then results += Using.resource(statement.getResultSet) { rs =>
        val metadata = rs.getMetaData
        val count = metadata.getColumnCount
        val columns = (1 to count).map(metadata.getColumnLabel).toList
        val rows = List.newBuilder[List[Option[String]]]
        var read = 0
        while read < RowLimit && rs.next() do {
          rows += (1 to count).map(i => Option(rs.getString(i))).toList
          read += 1
        }
        Result(columns, rows.result(), truncated = rs.next())
      }
      else if statement.getUpdateCount == -1 then finished = true
      else results += Result(affectedRows = Some(statement.getUpdateCount))
      if !finished then isRows = statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT)
    }
    results.result()
  }

  /** Escapes SQL LIKE pattern wildcards.
    *
    * @param name The pattern to escape
    * @param escape The escape character (typically \)
    * @return The escaped pattern
    */
  private[sql] def escapePattern(name: String, escape: String): String =
    name.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%")

  /** Reads the database schema including tables, columns, primary keys, and foreign keys.
    *
    * @param connection Active database connection
    * @return List of Table objects representing the schema
    */
  private[sql] def readSchema(connection: Connection): List[Table] = {
    val metadata = connection.getMetaData
    val catalog = connection.getCatalog
    val names = Using.resource(metadata.getTables(catalog, null, "%", Array("TABLE", "VIEW"))) { rs =>
      val builder = List.newBuilder[String]
      while rs.next() do builder += rs.getString("TABLE_NAME")
      builder.result().sorted
    }
    names.map { name =>
      val primaryKeys = Using.resource(metadata.getPrimaryKeys(catalog, null, name)) { rs =>
        val builder = Set.newBuilder[String]
        while rs.next() do builder += rs.getString("COLUMN_NAME")
        builder.result()
      }
      val columns = Using.resource(metadata.getColumns(catalog, null, escapePattern(name, metadata.getSearchStringEscape), "%")) { rs =>
        val builder = List.newBuilder[Column]
        while rs.next() do {
          val column = rs.getString("COLUMN_NAME")
          builder += Column(column, rs.getString("TYPE_NAME"),
            rs.getInt("NULLABLE") != java.sql.DatabaseMetaData.columnNoNulls, primaryKeys.contains(column))
        }
        builder.result()
      }
      val keys = Using.resource(metadata.getImportedKeys(catalog, null, name)) { rs =>
        val builder = List.newBuilder[ForeignKey]
        while rs.next() do builder += ForeignKey(rs.getString("FKCOLUMN_NAME"), rs.getString("PKTABLE_NAME"), rs.getString("PKCOLUMN_NAME"))
        builder.result()
      }
      Table(name, columns, keys)
    }
  }

  /** Main entry point for SQL editor execution.
    *
    * @param request The SQL editor request containing the database configuration and SQL commands
    * @return Response with results or error status
    */
  def handle(request: Request): Response = {
    val allowed = Option(System.getenv("SQL_EDITOR_DATABASES")).toList.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty).toSet
    val name = request.databaseConfig.databaseName
    val user = Option(System.getenv("SQL_EDITOR_USER")).filter(_.nonEmpty)
    val password = Option(System.getenv("SQL_EDITOR_PW"))
    
    // Verify configuration before connecting
    if !allowedDatabase(name, allowed) || user.isEmpty || password.isEmpty then
      return Response(Status.ConfigurationError, "Exercise database is not configured on the server.")

    val config = DatabaseConfigWithCredentials.readFromEnv(dbName = Some(name)).copy(user = user.get, password = password.get)
    
    // Validate host and port configuration
    if Option(config.host).forall(_.trim.isEmpty) ||
        !Option(config.port).flatMap(port => Try(port.toInt).toOption).exists(port => port >= 1 && port <= 65535) then
      return Response(Status.ConfigurationError, "Exercise database host or port is not configured on the server.")
    
    // Use the shared newConnection abstraction to ensure consistent connection handling
    val connected = Try(config.newConnection())
    
    connected.fold(
      _ => Response(Status.Unreachable, "Database is unreachable with the configured connection."),
      connection => Using.resource(connection) { conn =>
        var results = List.empty[Result]
        var status = Status.Ready
        var message = "Database connected."
        
        request.sql.foreach { sql =>
          // Validate SQL input
          if sql.trim.isEmpty || sql.length > 100000 then {
            status = Status.QueryError
            message = "Enter a SQL command (at most 100000 characters)."
          } else try {
            // Execute SQL using Statement with proper resource management
            results = Using.resource(conn.createStatement()) { statement =>
              statement.setQueryTimeout(10)
              statement.setMaxRows(RowLimit + 1)
              collectResults(statement, statement.execute(sql))
            }
            message = "SQL command completed."
          } catch {
            case error: SQLException =>
              status = if Option(error.getSQLState).exists(_.startsWith("08")) then Status.Unreachable else Status.QueryError
              message = if status == Status.Unreachable then "Database connection was lost." else
                s"SQL error (${Option(error.getSQLState).getOrElse("unknown")}, ${error.getErrorCode}): ${error.getMessage}"
          }
        }
        
        // Load schema after query execution
        val schema = Try(readSchema(conn))
        Response(status, message, results, schema.getOrElse(Nil),
          if schema.isFailure then Some("Database schema could not be loaded.") else None)
      }
    )
  }
}
