package it.evadid.server.commandHandler.sql.sync

import it.evadid.workbook.interaction.sync.model.HistoryJson

import it.evadid.distribution.commandTypes.SQLCommands.{DbFetchResponse, FetchAllFromDbRequest}
import it.evadid.server.commandHandler.sql.{DatabaseConfig, GenericSqlFunctionality}
import it.evadid.util.logging.Logger
import it.evadid.workbook.interaction.sync.{SyncContext, UsageContext}

import java.sql.*

case class FetchFromDatabase(
                              connection: Connection,
                              usageContext: UsageContext,
                              logger: Logger
                            ) {

  private lazy val generic: GenericSqlFunctionality = new GenericSqlFunctionality(connection,  logger)

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

    val asList: List[RichDatabaseEntry] = generic.executeSyncQuere(stmt, List("eventid", "serializeddata"), usageContext)
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

    val asList: List[RichDatabaseEntry] = generic.executeSyncQuere(stmt, List("eventid", "eventkey", "serializeddata"), usageContext)
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

    val asList: List[RichDatabaseEntry] = generic.executeSyncQuere(stmt, List("eventid", "eventkey", "serializeddata"), usageContext)

    if (asList.isEmpty) logger.logWarn("No events found for key: " + keyForSerialisation)
    else if (asList.size > 1) logger.logWarn("Multiple events found for key: " + keyForSerialisation + ", discarded events " + asList.tail.map(_.eventId).mkString(", "))

    asList.headOption
  }


}


object FetchFromDatabase {

  private def union(list: List[String]): String = HistoryJson.merge(list)

  def handleRequest(request: FetchAllFromDbRequest, logger: Logger): DbFetchResponse = {
    val config = DatabaseConfig.readFromEnv(Some(request.databaseName))
    val connection = config.newConnection()

    val control = FetchFromDatabase(connection, request.usageContext, logger)

    val resMap: Map[SyncContext, List[RichDatabaseEntry]] = {
      if (!request.hasDatabaseKeyColumn) control.fetchAllInDbWithoutKeys("events")
      else if (request.mayLimitToKey.isEmpty) control.fetchAllInDbWithKeys()
      else control.fetchForKeyInDbWithKey(request.mayLimitToKey.get).map(e => Map(e.syncContext -> List(e))).getOrElse(Map.empty)
    }

    DbFetchResponse(resMap.iterator.map(tup => tup._1 -> union(tup._2.map(_.historyJson))).toMap)
  }

}



