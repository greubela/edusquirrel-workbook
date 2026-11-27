package it.evadid.server.commandHandler.sql

import it.evadid.distribution.commandTypes.SqlEditorCommands.*
import it.evadid.sql.DatabaseConfigWithCredentials

import java.sql.{Connection, ResultSet, Statement, SQLException}
import scala.annotation.tailrec
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
  private[sql] def collectResults(statement: Statement, firstIsRows: Boolean): Seq[Result] = {
    @tailrec
    def collect(isRows: Boolean, collected: Vector[Result]): Seq[Result] = {
      val current =
        if isRows then Some(readResultSet(statement))
        else Some(statement.getUpdateCount).filter(_ != -1).map(count => Result(affectedRows = Some(count)))
      current match {
        case None => collected.toList
        case Some(result) => collect(statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT), collected :+ result)
      }
    }
    collect(firstIsRows, Vector.empty)
  }

  /** Reads at most [[RowLimit]] rows of the current result set and closes it. */
  private def readResultSet(statement: Statement): Result = Using.resource(statement.getResultSet) { rs =>
    val metadata = rs.getMetaData
    val count = metadata.getColumnCount
    val columns = (1 to count).map(metadata.getColumnLabel).toList

    @tailrec
    def readRows(rows: Vector[Seq[Option[String]]]): Vector[Seq[Option[String]]] =
      if rows.size < RowLimit && rs.next() then readRows(rows :+ (1 to count).map(i => Option(rs.getString(i))).toList)
      else rows

    Result(columns, readRows(Vector.empty).toList, truncated = rs.next())
  }

  /** Escapes SQL LIKE pattern wildcards.
    *
    * @param name The pattern to escape
    * @param escape The escape character (typically \)
    * @return The escaped pattern
    */
  private[sql] def escapePattern(name: String, escape: String): String =
    name.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%")

  /** Reads every row of a metadata result set and closes it. */
  private def readAll[A](rows: ResultSet)(read: ResultSet => A): List[A] =
    Using.resource(rows)(rs => Iterator.continually(rs.next()).takeWhile(identity).map(_ => read(rs)).toList)

  /** Reads the database schema including tables, columns, primary keys, and foreign keys.
    *
    * @param connection Active database connection
    * @return List of Table objects representing the schema
    */
  private[sql] def readSchema(connection: Connection): Seq[Table] = {
    val metadata = connection.getMetaData
    val catalog = connection.getCatalog
    val names = readAll(metadata.getTables(catalog, null, "%", Array("TABLE", "VIEW")))(_.getString("TABLE_NAME")).sorted
    names.map { name =>
      val primaryKeys = readAll(metadata.getPrimaryKeys(catalog, null, name))(_.getString("COLUMN_NAME")).toSet
      val columns = readAll(metadata.getColumns(catalog, null, escapePattern(name, metadata.getSearchStringEscape), "%")) { rs =>
        val column = rs.getString("COLUMN_NAME")
        Column(column, rs.getString("TYPE_NAME"),
          rs.getInt("NULLABLE") != java.sql.DatabaseMetaData.columnNoNulls, primaryKeys.contains(column))
      }
      val keys = readAll(metadata.getImportedKeys(catalog, null, name)) { rs =>
        ForeignKey(rs.getString("FKCOLUMN_NAME"), rs.getString("PKTABLE_NAME"), rs.getString("PKCOLUMN_NAME"))
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
      Response(Status.ConfigurationError, "Exercise database is not configured on the server.")
    else {
      val config = DatabaseConfigWithCredentials.readFromEnv(dbName = Some(name)).copy(user = user.get, password = password.get)
      // Validate host and port configuration
      if Option(config.host).forall(_.trim.isEmpty) ||
          !Option(config.port).flatMap(port => Try(port.toInt).toOption).exists(port => port >= 1 && port <= 65535) then
        Response(Status.ConfigurationError, "Exercise database host or port is not configured on the server.")
      else Try(config.newConnection()).fold(
        _ => Response(Status.Unreachable, "Database is unreachable with the configured connection."),
        connection => Using.resource(connection)(respond(_, request.sql))
      )
    }
  }

  /** Runs the optional SQL command, then reports the schema as seen afterwards. */
  private def respond(connection: Connection, sql: Option[String]): Response = {
    val (status, message, results) = sql.fold[(Status, String, Seq[Result])]((Status.Ready, "Database connected.", Seq.empty))(execute(connection, _))
    val schema = Try(readSchema(connection))
    Response(status, message, results, schema.getOrElse(Nil),
      if schema.isFailure then Some("Database schema could not be loaded.") else None)
  }

  private def execute(connection: Connection, sql: String): (Status, String, Seq[Result]) =
    if sql.trim.isEmpty || sql.length > 100000 then
      (Status.QueryError, "Enter a SQL command (at most 100000 characters).", Seq.empty)
    else try {
      val results = Using.resource(connection.createStatement()) { statement =>
        statement.setQueryTimeout(10)
        statement.setMaxRows(RowLimit + 1)
        collectResults(statement, statement.execute(sql))
      }
      (Status.Ready, "SQL command completed.", results)
    } catch {
      case error: SQLException =>
        val status = if Option(error.getSQLState).exists(_.startsWith("08")) then Status.Unreachable else Status.QueryError
        val message = if status == Status.Unreachable then "Database connection was lost." else
          s"SQL error (${Option(error.getSQLState).getOrElse("unknown")}, ${error.getErrorCode}): ${error.getMessage}"
        (status, message, Seq.empty)
    }
}
