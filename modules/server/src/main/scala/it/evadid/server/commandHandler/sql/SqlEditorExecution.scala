package it.evadid.server.commandHandler.sql

import it.evadid.distribution.commandTypes.SqlEditorCommands.*
import java.sql.{Connection, DriverManager, SQLException, Statement}
import scala.util.{Try, Using}

/** Executes only on explicitly configured teaching databases, with dedicated credentials. */
object SqlEditorExecution {
  private val RowLimit = 500

  private[sql] def allowedDatabase(name: String, allowed: Set[String]): Boolean =
    name.matches("[A-Za-z0-9_]+") && allowed.contains(name)

  def handle(request: Request): Response = {
    val allowed = Option(System.getenv("SQL_EDITOR_DATABASES")).toList.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty).toSet
    val name = request.databaseConfig.databaseName
    val user = Option(System.getenv("SQL_EDITOR_USER")).filter(_.nonEmpty)
    val password = Option(System.getenv("SQL_EDITOR_PW"))
    if !allowedDatabase(name, allowed) || user.isEmpty || password.isEmpty then
      return Response(Status.ConfigurationError, "Exercise database is not configured on the server.")

    val config = DatabaseConfig.readFromEnv(Some(name)).copy(user = user.get, password = password.get)
    if Option(config.host).forall(_.trim.isEmpty) ||
        !Option(config.port).flatMap(port => Try(port.toInt).toOption).exists(port => port >= 1 && port <= 65535) then
      return Response(Status.ConfigurationError, "Exercise database host or port is not configured on the server.")
    val connected = Try(DriverManager.getConnection(
      config.jdbcUrl + "?connectTimeout=5000&socketTimeout=15000&allowMultiQueries=false", config.user, config.password))
    connected.fold(
      _ => Response(Status.Unreachable, "Database is unreachable with the configured connection."),
      connection => Using.resource(connection) { conn =>
        var results = List.empty[Result]
        var status = Status.Ready
        var message = "Database connected."
        request.sql.foreach { sql =>
          if sql.trim.isEmpty || sql.length > 100000 then {
            status = Status.QueryError
            message = "Enter a SQL command (at most 100000 characters)."
          } else try {
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
        val schema = Try(readSchema(conn))
        Response(status, message, results, schema.getOrElse(Nil),
          if schema.isFailure then Some("Database schema could not be loaded.") else None)
      }
    )
  }

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

  private[sql] def escapePattern(name: String, escape: String): String =
    name.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%")

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
}
