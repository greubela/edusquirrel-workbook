package it.evadid.distribution.commandTypes

import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.ExecutionCommandFactory
import it.evadid.workbook.elements.interactionElements.sql.SqlDatabaseConfig
import upickle.default.*

object SqlEditorCommands {
  final case class Request(databaseConfig: SqlDatabaseConfig, sql: Option[String] = None) derives upickle.default.ReadWriter
  final case class Column(name: String, dataType: String, nullable: Boolean, primaryKey: Boolean) derives upickle.default.ReadWriter
  final case class ForeignKey(column: String, targetTable: String, targetColumn: String) derives upickle.default.ReadWriter
  final case class Table(name: String, columns: List[Column], foreignKeys: List[ForeignKey]) derives upickle.default.ReadWriter
  final case class Result(columns: List[String] = Nil, rows: List[List[Option[String]]] = Nil,
      affectedRows: Option[Int] = None, truncated: Boolean = false) derives upickle.default.ReadWriter
  enum Status {
    case Ready, Unreachable, ConfigurationError, QueryError
  }
  object Status { given ReadWriter[Status] = readwriter[String].bimap[Status](_.toString, Status.valueOf) }
  final case class Response(status: Status, message: String = "", results: List[Result] = Nil,
      schema: List[Table] = Nil, schemaError: Option[String] = None) derives upickle.default.ReadWriter
  lazy val execute = ExecutionCommandFactory[Request, Response](
    "sql-editor-execute", Serializer.fromImplicitRW[Request], Serializer.fromImplicitRW[Response],
    request => s"SQL editor database: ${request.databaseConfig.databaseName}",
    response => s"SQL editor status: ${response.status}")
}
