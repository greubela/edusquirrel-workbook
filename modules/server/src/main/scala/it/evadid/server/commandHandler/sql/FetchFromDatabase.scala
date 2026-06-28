package it.evadid.server.commandHandler.sql

import it.evadid.util.Logger
import it.evadid.workbook.model.interaction.sync.SyncFormatter.{RichInteractionVariableFormatter, RichInteractionVariableHistorySerialized}
import it.evadid.workbook.model.interaction.sync.{SyncContext, UsageContext}

import java.sql.*
import scala.collection.mutable

case class FetchFromDatabase(
                              connection: Connection,
                              context: UsageContext,
                              logger: Logger,
                              formatter: RichInteractionVariableFormatter
                            ) {


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

  private def executeQuery(preparedStatement: PreparedStatement, fieldsToRead: List[String]): List[RichDatabaseEntry] = {
    logger.logInfo("Executing query: " + preparedStatement.toString)
    try {
      val rs = preparedStatement.executeQuery()
      logger.logInfo("Query executed successfully")
      val asList = readAll(rs, fieldsToRead)
      convertAll(asList, tup => RichDatabaseEntry(context, formatter, tup))
    } catch case e: Exception => {
      preparedStatement.close()
      logger.logError(s"Error fetching from database: ${e.getMessage}")
      throw e
    }
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


  def fetchAllInDbWithoutKeys(): Map[SyncContext, RichInteractionVariableHistorySerialized] = {
    logger.logInfo("Fetching all events from database (without keys) for usage context: " + context.toString)

    val sql: String = {
      """
        |SELECT `eventid`, `serializeddata`
        |FROM `events`
        |WHERE `programid` = ? AND `scenarioid` = ? AND `userid` = ?
        |ORDER BY `timestamp` DESC
        |""".stripMargin
    }

    val stmt = connection.prepareStatement(sql)
    stmt.setString(1, context.programId)
    stmt.setString(2, context.scenarioId)
    stmt.setString(3, context.userId)

    val asList: List[RichDatabaseEntry] = executeQuery(stmt, List("eventid", "serializeddata"))
    asList.map(_.toMapEntry).toMap
  }

  def fetchAllInDbWithKeys(): Map[SyncContext, RichInteractionVariableHistorySerialized] = {
    logger.logInfo("Fetching all events from database (with keys) for usage context: " + context.toString)

    val sql: String = {
      """
        |SELECT `eventid`, `eventkey`, `serializeddata`
        |FROM `eventsWithKey`
        |WHERE `programid` = ? AND `scenarioid` = ? AND `userid` = ?
        |ORDER BY `timestamp` DESC
        |""".stripMargin
    }

    val stmt = connection.prepareStatement(sql)
    stmt.setString(1, context.programId)
    stmt.setString(2, context.scenarioId)
    stmt.setString(3, context.userId)

    val asList: List[RichDatabaseEntry] = executeQuery(stmt, List("eventid", "eventkey", "serializeddata"))
    asList.map(_.toMapEntry).toMap
  }

  def fetchForKeyInDbWithKey(keyForSerialisation: String): Option[RichDatabaseEntry] = {
    val syncContext = context.toSyncContext(keyForSerialisation)
    logger.logInfo("Fetching all events from database (with keys) for context: " + syncContext)

    val sql: String =
      """
        |SELECT `eventid`, `eventkey`, `serializeddata`
        |FROM `eventsWithKey`
        |WHERE `programid` = ? AND `scenarioid` = ? AND `userid` = ? AND `eventkey` = ?
        |ORDER BY `timestamp` DESC
        |""".stripMargin

    val stmt = connection.prepareStatement(sql)
    stmt.setString(1, context.programId)
    stmt.setString(2, context.scenarioId)
    stmt.setString(3, context.userId)
    stmt.setString(4, keyForSerialisation)

    val asList: List[RichDatabaseEntry] = executeQuery(stmt, List("eventid", "eventkey", "serializeddata"))

    if (asList.isEmpty) logger.logWarn("No events found for key: " + keyForSerialisation)
    else if (asList.size > 1) logger.logWarn("Multiple events found for key: " + keyForSerialisation + ", discarded events " + converted.tail.map(_.eventId).mkString(", "))

    asList.headOption
  }


}



