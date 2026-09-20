package it.evadid.server.commandHandler.sql

import it.evadid.server.commandHandler.sql.sync.RichDatabaseEntry
import it.evadid.util.logging.Logger
import it.evadid.workbook.interaction.sync.SyncFormatter.RichInteractionVariableFormatter
import it.evadid.workbook.interaction.sync.UsageContext

import java.sql.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.collection.mutable

private[sql] case class GenericSqlFunctionality(
                                                 connection: Connection,
                                                 logger: Logger
                                               ) {

  def executeUpdate(statement: PreparedStatement): Int = {
    try {
      val res = statement.executeUpdate()
      statement.clearParameters()
      logger.logInfo(s"Executed Update statement: ${statement.toString}")
      res
    } catch case e: Exception => {
      statement.close()
      logger.logError(s"Error updating database: ${e.getMessage}")
      throw e
    }
  }

  def executeQuery(preparedStatement: PreparedStatement, fieldsToRead: List[String]): List[List[String]] = {
    logger.logInfo("Executing query: " + preparedStatement.toString)
    try {
      val rs = preparedStatement.executeQuery()
      val asList: List[List[String]] = readAll(rs, fieldsToRead)
      logger.logInfo(s"Query executed successfully, ${asList.size} results!")
      asList
    } catch case e: Exception => {
      preparedStatement.close()
      logger.logError(s"Error fetching from database: ${e.getMessage}")
      throw e
    }
  }

  def parseDatabaseTimestamp(databaseTimestampString: String): LocalDateTime = {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val localDateTime: LocalDateTime = LocalDateTime.parse(databaseTimestampString, formatter)
    localDateTime
  }

  def executeSyncQuere(preparedStatement: PreparedStatement, fieldsToRead: List[String], usageContext: UsageContext, formatter: RichInteractionVariableFormatter): List[RichDatabaseEntry] = {
    val asList = executeQuery(preparedStatement, fieldsToRead)
    convertAll(asList, tup => RichDatabaseEntry(usageContext, formatter, tup))
  }

  private def readOne(resultSet: ResultSet, fields: List[String]): Option[List[String]] = try {
    Some(fields.map(resultSet.getString))
  } catch case e: Exception => {
    logger.logWarn(s"Could not parse element from result set because of the following error: " + e.getMessage)
    None
  }

  private def readAll(resultSet: ResultSet, fields: List[String]): List[List[String]] = {
    val events = mutable.ListBuffer[Option[List[String]]]()
    events += readOne(resultSet, fields)
    while (resultSet.next()) events += readOne(resultSet, fields)
    val res = events.toList.flatten
    logger.logInfo(s"read ${events.size} events from database with the following ${fields.size} fields: ${fields.mkString(",")}")
    res
  }

  private def convertOne[T](list: List[String], func: List[String] => T): Option[T] = {
    try {
      Some(func(list))
    } catch case e: Exception => {
      logger.logWarn(s"Could not parse element from list because of the following error: " + e.getMessage)
      None
    }
  }

  private def convertAll[T](list: List[List[String]], func: List[String] => T): List[T] = {
    val events = list.flatMap(convertOne(_, func))
    logger.logInfo(s"parsed ${events.size} events (could not parse ${list.size - events.size})")
    events
  }


}
