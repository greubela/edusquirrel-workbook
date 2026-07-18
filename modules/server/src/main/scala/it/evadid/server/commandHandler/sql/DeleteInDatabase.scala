package it.evadid.server.commandHandler.sql

import it.evadid.distribution.commandTypes.SQLCommands.DeleteInDbRequest
import it.evadid.util.logging.Logger
import it.evadid.workbook.interaction.sync.{SyncContext, UsageContext}
import it.evadid.workbook.interaction.sync.SyncFormatter.RichInteractionVariableFormatter
import it.evadid.workbook.interaction.sync.SyncInformation.SyncSuccess

import java.sql.{Connection, PreparedStatement}
import java.time.LocalDateTime

case class DeleteInDatabase(
                             connection: Connection,
                             usageContext: UsageContext,
                             logger: Logger,
                             formatter: RichInteractionVariableFormatter
                           ) {

  private lazy val generic: GenericSqlFunctionality = new GenericSqlFunctionality(connection, usageContext, logger, formatter)
  private lazy val fetch: FetchFromDatabase = new FetchFromDatabase(connection, usageContext, logger, formatter)

  private def deleteEventsById(ids: Set[Long], tableName: String): SyncSuccess = {
    logger.logInfo(s"deleting ${ids.size} events from database")
    if (ids.isEmpty) SyncSuccess(0, 0, 0, LocalDateTime.now())
    else {
      val placeholders = ids.map(_ => "?").mkString(", ")
      val stmt = connection.prepareStatement(s"DELETE FROM `$tableName` WHERE `eventid` IN ($placeholders)")
      ids.zipWithIndex.foreach { case (id, index) => stmt.setLong(index + 1, id) }
      val res = generic.executeUpdate(stmt)
      println(s"Deleted $res event from database (${ids.size - res} could not be deleted)")
      SyncSuccess(0, 0, res, LocalDateTime.now())
    }
  }

  def deleteByKeyInDatabaseWithKey(key: String): SyncSuccess = {
    logger.logInfo(s"deleting all events for key $key from database with keys")
    val sql: String =
      """
        |DELETE FROM `eventsWithKey`
        |WHERE `programid` = ? AND `scenarioid` = ? AND `userid` = ? AND `eventkey` = ?
        |""".stripMargin

    val stmt: PreparedStatement = connection.prepareStatement(sql)
    stmt.setString(1, usageContext.programId)
    stmt.setString(2, usageContext.scenarioId)
    stmt.setString(3, usageContext.userId)
    stmt.setString(4, key)

    val res = generic.executeUpdate(stmt)
    println(s"Deleted $res event from database")
    SyncSuccess(0, 0, res, LocalDateTime.now())
  }

  def deleteByKeyInDatabaseWithoutKey(key: String, tableName: String = "events"): SyncSuccess = {
    logger.logInfo(s"deleting all events for key $key from database with keys")

    val mySyncContext = usageContext.toSyncContext(key)
    val fetched: Map[SyncContext, List[RichDatabaseEntry]] = fetch.fetchAllInDbWithoutKeys(tableName)

    logger.logInfo(s"fetched ${fetched.keySet.size} contexts, is mine present: ${fetched.contains(mySyncContext)}")
    if (fetched.contains(mySyncContext)) {
      val ids: List[Long] = fetched(mySyncContext).map(_.eventId.toLong)
      deleteEventsById(ids.toSet, tableName)
    } else {
      logger.logInfo("fetched does not contain my context, skipping delete")
      SyncSuccess(0, 0, 0, LocalDateTime.now())
    }
  }

  def deleteAllEventsOfUsage(tableName: String): SyncSuccess = {
    logger.logInfo(s"deleting all events for usage $usageContext from database")
    val sql: String =
      s"""
         |DELETE FROM `$tableName`
         |WHERE `programid` = ? AND `scenarioid` = ? AND `userid` = ?
         |""".stripMargin

    val stmt: PreparedStatement = connection.prepareStatement(sql)
    stmt.setString(1, usageContext.programId)
    stmt.setString(2, usageContext.scenarioId)
    stmt.setString(3, usageContext.userId)

    val res = generic.executeUpdate(stmt)
    println(s"Deleted $res event from database")
    SyncSuccess(0, 0, res, LocalDateTime.now())
  }

}

object DeleteInDatabase {

  def handleRequest(request: DeleteInDbRequest, logger: Logger): SyncSuccess = {
    val config = DatabaseConfig.readFromEnv(request.databaseName)
    val connection = config.newConnection()

    val control = DeleteInDatabase(connection, request.usageContext, logger, request.formatter)

    if (request.limitToKey.isEmpty) {
      if (request.hasDatabaseKeyColumn) control.deleteAllEventsOfUsage("eventsWithKey")
      else control.deleteAllEventsOfUsage("events")
    } else {
      if (request.hasDatabaseKeyColumn) control.deleteByKeyInDatabaseWithKey(request.limitToKey.get)
      else control.deleteByKeyInDatabaseWithoutKey(request.limitToKey.get, "events")
    }
  }


}
