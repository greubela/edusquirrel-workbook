package it.evadid.server.commandHandler.sql

import it.evadid.server.database.SqlFailure
import it.evadid.core.util.io.ParsingContext
import it.evadid.util.logging.Logger
import it.evadid.workbook.interaction.sync.UsageContext

import java.sql.*
import java.time.LocalDateTime
import scala.util.{Try, Using}
import scala.util.control.NonFatal

private[sql] case class GenericSqlFunctionality(logger: Logger) {

  private def execute[A](statement: PreparedStatement, description: String)(operation: => A): A =
    try SqlFailure(description, logger)(operation)
    catch case NonFatal(error) =>
      Try(statement.close()).failed.toOption.filterNot(_ eq error).foreach(error.addSuppressed)
      logger.logError(s"Error $description database: ${error.getMessage}")
      throw error

  def executeUpdate(statement: PreparedStatement): Int = execute(statement, "executing database update") {
    val result = statement.executeUpdate()
    statement.clearParameters()
    logger.logInfo(s"Executed Update statement: $statement")
    result
  }

  def executeQuery(statement: PreparedStatement, fieldsToRead: Seq[String]): Seq[Seq[String]] = execute(statement, s"reading database columns ${fieldsToRead.mkString(", ")}") {
    logger.logInfo("Executing query: " + statement)
    val rows = Using.resource(statement.executeQuery())(readAll(_, fieldsToRead))
    logger.logInfo(s"Query executed successfully, ${rows.size} results!")
    rows
  }

  def parseDatabaseTimestamp(databaseTimestampString: String): LocalDateTime =
    Timestamp.valueOf(databaseTimestampString).toLocalDateTime

  private def readSafely[A](description: String)(read: => A): Option[A] = Try(read).fold(
    error => { logger.logWarn(s"$description because of the following error: ${ParsingContext.reason(error)}"); None },
    Some(_))

  private def readAll(resultSet: ResultSet, fields: Seq[String]): Seq[Seq[String]] = {
    val res = Iterator.continually(resultSet.next()).takeWhile(identity)
      .flatMap(_ => readSafely("Could not parse element from result set")(fields.map(resultSet.getString))).toList
    logger.logInfo(s"read ${res.size} rows from database with the following ${fields.size} fields: ${fields.mkString(",")}")
    res
  }

}
