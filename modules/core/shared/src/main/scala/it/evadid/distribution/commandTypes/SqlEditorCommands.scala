package it.evadid.distribution.commandTypes

import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.ExecutionCommandFactory
import it.evadid.workbook.elements.interactionElements.sql.SqlDatabaseConfig
import upickle.default.*

object SqlEditorCommands {
  final case class Request(databaseConfig: SqlDatabaseConfig, sql: Option[String] = None)
  object Request { given ReadWriter[Request] = macroRW }
  final case class Column(name: String, dataType: String, nullable: Boolean, primaryKey: Boolean)
  object Column { given ReadWriter[Column] = macroRW }
  final case class ForeignKey(column: String, targetTable: String, targetColumn: String)
  object ForeignKey { given ReadWriter[ForeignKey] = macroRW }
  final case class Table(name: String, columns: List[Column], foreignKeys: List[ForeignKey])
  object Table { given ReadWriter[Table] = macroRW }
  final case class Result(columns: List[String] = Nil, rows: List[List[Option[String]]] = Nil,
      affectedRows: Option[Int] = None, truncated: Boolean = false)
  object Result { given ReadWriter[Result] = macroRW }
  enum Status {
    case Ready, Unreachable, ConfigurationError, QueryError
  }
  object Status { given ReadWriter[Status] = readwriter[String].bimap[Status](_.toString, Status.valueOf) }
  final case class Response(status: Status, message: String = "", results: List[Result] = Nil,
      schema: List[Table] = Nil, schemaError: Option[String] = None)
  object Response { given ReadWriter[Response] = macroRW }
  lazy val execute = ExecutionCommandFactory[Request, Response](
    "sql-editor-execute", Serializer.fromImplicitRW[Request], Serializer.fromImplicitRW[Response],
    request => s"SQL editor database: ${request.databaseConfig.databaseName}",
    response => s"SQL editor status: ${response.status}")
}
