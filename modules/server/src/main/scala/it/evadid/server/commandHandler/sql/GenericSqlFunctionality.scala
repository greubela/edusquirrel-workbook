package it.evadid.server.commandHandler.sql

import it.evadid.server.commandHandler.sql.sync.RichDatabaseEntry
import it.evadid.util.logging.Logger
import it.evadid.workbook.interaction.sync.UsageContext

import java.sql.*
import java.time.LocalDateTime
import scala.util.{Try, Using}
import scala.util.control.NonFatal

private[sql] case class GenericSqlFunctionality(logger: Logger) {

  private def execute[A](statement: PreparedStatement, description: String)(operation: => A): A =
    try operation
    catch case NonFatal(error) =>
      Try(statement.close()).failed.toOption.filterNot(_ eq error).foreach(error.addSuppressed)
      logger.logError(s"Error $description database: ${error.getMessage}")
      throw error

  def executeUpdate(statement: PreparedStatement): Int = execute(statement, "updating") {
    val result = statement.executeUpdate()
    statement.clearParameters()
    logger.logInfo(s"Executed Update statement: $statement")
    result
  }

  def executeQuery(statement: PreparedStatement, fieldsToRead: List[String]): List[List[String]] = execute(statement, "fetching from") {
    logger.logInfo("Executing query: " + statement)
    val rows = Using.resource(statement.executeQuery())(readAll(_, fieldsToRead))
    logger.logInfo(s"Query executed successfully, ${rows.size} results!")
    rows
  }

  def parseDatabaseTimestamp(databaseTimestampString: String): LocalDateTime =
    Timestamp.valueOf(databaseTimestampString).toLocalDateTime

  def executeSyncQuere(preparedStatement: PreparedStatement, fieldsToRead: List[String], usageContext: UsageContext): List[RichDatabaseEntry] = {
    val rows = executeQuery(preparedStatement, fieldsToRead)
    val entries = rows.flatMap(row => readSafely("Could not parse element from list") {
      val entry = RichDatabaseEntry(usageContext, row)
      // Validate lazy fields inside the per-record boundary.
      entry.keyForSerialisation
      it.evadid.workbook.interaction.sync.model.HistoryJson.lastTimestamp(entry.historyJson)
      entry
    })
    logger.logInfo(s"parsed ${entries.size} events (could not parse ${rows.size - entries.size})")
    entries
  }

  private def readSafely[A](description: String)(read: => A): Option[A] = Try(read).fold(
    error => { logger.logWarn(s"$description because of the following error: ${error.getMessage}"); None },
    Some(_))

  private def readAll(resultSet: ResultSet, fields: List[String]): List[List[String]] = {
    val res = Iterator.continually(resultSet.next()).takeWhile(identity)
      .flatMap(_ => readSafely("Could not parse element from result set")(fields.map(resultSet.getString))).toList
    logger.logInfo(s"read ${res.size} rows from database with the following ${fields.size} fields: ${fields.mkString(",")}")
    res
  }

}
