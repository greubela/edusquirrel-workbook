package it.evadid.server.sqlEditor

import it.evadid.distribution.commandTypes.SqlEditorCommands.*
import it.evadid.server.ServerConfig
import it.evadid.server.database.{Database, SqlStatements}
import it.evadid.sql.DatabaseConfigWithCredentials

import java.sql.{Connection, Statement, SQLException}
import scala.annotation.tailrec
import scala.util.{Try, Using}

/** Runs learners' SQL on allow-listed teaching databases of the backend's server. The SQL text is the exercise
  * itself and runs unprepared; protection comes from the allow-list and the dedicated, restricted editor account.
  * Results are bounded and report truncation.
  */
final class SqlEditorExecution(config: ServerConfig.SqlEditor, backend: Option[DatabaseConfigWithCredentials]) {
  import SqlEditorExecution.*

  def handle(request: Request): Response = {
    val name = request.databaseConfig.databaseName
    val target = for {
      (user, password) <- config.credentials if allowedDatabase(name, config.databases)
      server <- backend
    } yield server.copy(database = name, user = user, password = password)
    target.fold(Response(Status.ConfigurationError, "Exercise database is not configured on the server.")) { database =>
      Try(Database.open(database)).fold(
        _ => Response(Status.Unreachable, "Database is unreachable with the configured connection."),
        connection => Using.resource(connection)(respond(_, request.sql)))
    }
  }
}

object SqlEditorExecution {
  private val RowLimit = 500

  /** Only exact allow-listed names, which also cannot add JDBC URL parameters or paths. */
  private[sqlEditor] def allowedDatabase(name: String, allowed: Set[String]): Boolean =
    name.matches("[A-Za-z0-9_]+") && allowed.contains(name)

  /** Every result of an executed statement: bounded row sets and update counts. */
  private[sqlEditor] def collectResults(statement: Statement, firstIsRows: Boolean): Seq[Result] = {
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

  /** Tables and views with their columns, primary keys and references. Columns are read once for the whole catalog
    * and grouped by exact table name, so no table name is used as a metadata search pattern.
    */
  private[sqlEditor] def readSchema(connection: Connection): Seq[Table] = {
    val metadata = connection.getMetaData
    val catalog = connection.getCatalog
    val names = SqlStatements.rows(metadata.getTables(catalog, null, "%", Array("TABLE", "VIEW")))(_.getString("TABLE_NAME")).sorted
    val columns = SqlStatements.rows(metadata.getColumns(catalog, null, "%", "%")) { rs =>
      (rs.getString("TABLE_NAME"), rs.getString("COLUMN_NAME"), rs.getString("TYPE_NAME"),
        rs.getInt("NULLABLE") != java.sql.DatabaseMetaData.columnNoNulls)
    }.groupBy(_._1)
    names.map { name =>
      val primaryKeys = SqlStatements.rows(metadata.getPrimaryKeys(catalog, null, name))(_.getString("COLUMN_NAME")).toSet
      val keys = SqlStatements.rows(metadata.getImportedKeys(catalog, null, name)) { rs =>
        ForeignKey(rs.getString("FKCOLUMN_NAME"), rs.getString("PKTABLE_NAME"), rs.getString("PKCOLUMN_NAME"))
      }
      Table(name, columns.getOrElse(name, Nil).map((_, column, sqlType, nullable) =>
        Column(column, sqlType, nullable, primaryKeys.contains(column))), keys)
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
