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

  lazy val statements: List[Statement] = names.flatMap { name =>
    val sql = Using.resource(Source.fromResource(name))(_.mkString)
    CCJSqlParserUtil.parseStatements(sql).getStatements.asScala.toList
  }
  lazy val tables: List[CreateTable] = statements.collect { case table: CreateTable => table }
  def identifier(value: String): String = value.stripPrefix("`").stripSuffix("`")
}
