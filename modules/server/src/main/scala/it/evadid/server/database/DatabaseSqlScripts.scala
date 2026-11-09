package it.evadid.server.database

import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.create.table.CreateTable
import scala.jdk.CollectionConverters.*
import scala.io.Source
import scala.util.Using

/** The packaged SQL files are the sole schema definitions, shared with integration tests. */
object DatabaseSqlScripts {
  val names: List[String] = List("createUserTables.sql", "createLogTables.sql",
    "createClassicHistoryTables.sql", "createInteractionHistoryTables.sql")

  def statementsFor(name: String): List[Statement] = {
    require(names.contains(name), "Unknown database schema script")
    val sql = Using.resource(Source.fromResource(name))(_.mkString)
    CCJSqlParserUtil.parseStatements(sql).asScala.toList
  }
  lazy val statements: List[Statement] = names.flatMap(statementsFor)
  lazy val tables: List[CreateTable] = {
    val definitions = statements.collect { case table: CreateTable => table }
    val identifiers = definitions.map(table => identifier(table.getTable.getName))
    require(identifiers.distinct.size == identifiers.size, "Duplicate table definitions in database scripts")
    definitions
  }
  def identifier(value: String): String = value.stripPrefix("`").stripSuffix("`")
}
