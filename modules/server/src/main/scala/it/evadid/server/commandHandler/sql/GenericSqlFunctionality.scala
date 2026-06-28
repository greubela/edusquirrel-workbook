package it.evadid.server.commandHandler.sql

import it.evadid.util.logging.Logger
import it.evadid.workbook.model.interaction.sync.SyncFormatter.RichInteractionVariableFormatter
import it.evadid.workbook.model.interaction.sync.UsageContext

import java.sql.*
import scala.collection.mutable

private[sql] case class GenericSqlFunctionality(
                                                 connection: Connection,
                                                 usageContext: UsageContext,
                                                 logger: Logger,
                                                 formatter: RichInteractionVariableFormatter
                                               ) {


  def executeUpdate(statement: PreparedStatement): Int = {
    logger.logInfo(s"Executing Update statement: ${statement.toString}")
    try {
      statement.executeUpdate()
    } catch case e: Exception => {
      statement.close()
      logger.logError(s"Error updating database: ${e.getMessage}")
      throw e
    }
  }

  def executeQuery(preparedStatement: PreparedStatement, fieldsToRead: List[String]): List[RichDatabaseEntry] = {
    logger.logInfo("Executing query: " + preparedStatement.toString)
    try {
      val rs = preparedStatement.executeQuery()
      logger.logInfo("Query executed successfully")
      val asList = readAll(rs, fieldsToRead)
      convertAll(asList, tup => RichDatabaseEntry(usageContext, formatter, tup))
    } catch case e: Exception => {
      preparedStatement.close()
      logger.logError(s"Error fetching from database: ${e.getMessage}")
      throw e
    }
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
