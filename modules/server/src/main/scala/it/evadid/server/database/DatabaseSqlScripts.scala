package it.evadid.server.database

import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.create.table.CreateTable
import scala.jdk.CollectionConverters.*
import scala.io.Source
import scala.util.Using

/** The packaged setup script is the sole schema definition, shared with integration tests. */
object DatabaseSqlScripts {
  val name: String = "setup.sql"
  private val tableMarker = "-- BEGIN APPLICATION TABLES"
  private lazy val sql = Using.resource(Source.fromResource(name))(_.mkString)

  /** Backend initialization uses its existing configured connection, without CREATE DATABASE or USE. */
  lazy val statements: List[Statement] = {
    val marker = sql.indexOf(tableMarker)
    require(marker >= 0, s"Missing application-table marker in $name")
    CCJSqlParserUtil.parseStatements(sql.substring(marker + tableMarker.length)).asScala.toList
  }
  lazy val tables: List[CreateTable] = {
    val definitions = statements.collect { case table: CreateTable => table }
    val identifiers = definitions.map(table => identifier(table.getTable.getName))
    require(identifiers.distinct.size == identifiers.size, "Duplicate table definitions in database scripts")
    definitions
  }
  def identifier(value: String): String = value.stripPrefix("`").stripSuffix("`")
}
