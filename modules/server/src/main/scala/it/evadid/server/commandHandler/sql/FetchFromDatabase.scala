package it.evadid.server.commandHandler.sql

import it.evadid.distribution.commandTypes.SQLCommands.{DbFetchResponse, FetchAllFromDbRequest}
import it.evadid.util.Logger
import it.evadid.workbook.model.interaction.sync.SyncFormatter.RichInteractionVariableFormatter
import it.evadid.workbook.model.interaction.sync.{SyncContext, UsageContext}
import it.evadid.workbook.model.interaction.variable.InteractionVariableHistorySerialized

import java.sql.*

case class FetchFromDatabase(
                              connection: Connection,
                              usageContext: UsageContext,
                              logger: Logger,
                              formatter: RichInteractionVariableFormatter
                            ) {

  private lazy val generic: GenericSqlFunctionality = new GenericSqlFunctionality(connection, usageContext, logger, formatter)

  def fetchAllInDbWithoutKeys(tableName: String = "events"): Map[SyncContext, List[RichDatabaseEntry]] = {
    logger.logInfo("Fetching all events from database (without keys) for usage context: " + usageContext.toString)

    val sql: String = {
      s"""
         |SELECT `eventid`, `serializeddata`
         |FROM `$tableName`
         |WHERE `programid` = ? AND `scenarioid` = ? AND `userid` = ?
         |ORDER BY `timestamp` DESC
         |""".stripMargin
    }

    val stmt = connection.prepareStatement(sql)
    stmt.setString(1, usageContext.programId)
    stmt.setString(2, usageContext.scenarioId)
    stmt.setString(3, usageContext.userId)

    val asList: List[RichDatabaseEntry] = generic.executeQuery(stmt, List("eventid", "serializeddata"))
    asList.groupBy(_.syncContext).toMap
  }

  def fetchAllInDbWithKeys(): Map[SyncContext, List[RichDatabaseEntry]] = {
    logger.logInfo("Fetching all events from database (with keys) for usage context: " + usageContext.toString)

    val sql: String = {
      """
        |SELECT `eventid`, `eventkey`, `serializeddata`
        |FROM `eventsWithKey`
        |WHERE `programid` = ? AND `scenarioid` = ? AND `userid` = ?
        |ORDER BY `timestamp` DESC
        |""".stripMargin
    }

    val stmt = connection.prepareStatement(sql)
    stmt.setString(1, usageContext.programId)
    stmt.setString(2, usageContext.scenarioId)
    stmt.setString(3, usageContext.userId)

    val asList: List[RichDatabaseEntry] = generic.executeQuery(stmt, List("eventid", "eventkey", "serializeddata"))
    asList.groupBy(_.syncContext).toMap
  }

  def fetchForKeyInDbWithKey(keyForSerialisation: String): Option[RichDatabaseEntry] = {
    val syncContext = usageContext.toSyncContext(keyForSerialisation)
    logger.logInfo("Fetching all events from database (with keys) for context: " + syncContext)

    val sql: String =
      """
        |SELECT `eventid`, `eventkey`, `serializeddata`
        |FROM `eventsWithKey`
        |WHERE `programid` = ? AND `scenarioid` = ? AND `userid` = ? AND `eventkey` = ?
        |ORDER BY `timestamp` DESC
        |""".stripMargin

    val stmt = connection.prepareStatement(sql)
    stmt.setString(1, usageContext.programId)
    stmt.setString(2, usageContext.scenarioId)
    stmt.setString(3, usageContext.userId)
    stmt.setString(4, keyForSerialisation)

    val asList: List[RichDatabaseEntry] = generic.executeQuery(stmt, List("eventid", "eventkey", "serializeddata"))

    if (asList.isEmpty) logger.logWarn("No events found for key: " + keyForSerialisation)
    else if (asList.size > 1) logger.logWarn("Multiple events found for key: " + keyForSerialisation + ", discarded events " + asList.tail.map(_.eventId).mkString(", "))

    asList.headOption
  }


}


object FetchFromDatabase {

  private def union(list: List[InteractionVariableHistorySerialized]): InteractionVariableHistorySerialized = {
    InteractionVariableHistorySerialized(list.flatMap(_.states).toSet)
  }

  def handleRequest(request: FetchAllFromDbRequest, logger: Logger): DbFetchResponse = {
    val config = DatabaseConfig.readFromEnv(request.databaseName)
    val connection = config.newConnection()

    val control = FetchFromDatabase(connection, request.usageContext, logger, request.formatter)

    val resMap: Map[SyncContext, List[RichDatabaseEntry]] = {
      if (!request.hasDatabaseKeyColumn) control.fetchAllInDbWithoutKeys("events")
      else if (request.mayLimitToKey.isEmpty) control.fetchAllInDbWithKeys()
      else control.fetchForKeyInDbWithKey(request.mayLimitToKey.get).map(e => Map(e.syncContext -> List(e))).getOrElse(Map.empty)
    }

    DbFetchResponse(resMap.iterator.map(tup => tup._1 -> union(tup._2.map(_.richHistory.fullHistory))).toMap)
  }

}



