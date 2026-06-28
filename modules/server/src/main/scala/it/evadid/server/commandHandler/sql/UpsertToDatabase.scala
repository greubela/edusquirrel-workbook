package it.evadid.server.commandHandler.sql

import it.evadid.distribution.commandTypes.SQLCommands.StoreToDbRequest
import it.evadid.util.Logger
import it.evadid.workbook.model.interaction.sync.SyncFormatter.RichInteractionVariableFormatter
import it.evadid.workbook.model.interaction.sync.SyncInformation.SyncSuccess
import it.evadid.workbook.model.interaction.sync.UsageContext
import it.evadid.workbook.model.interaction.variable.InteractionVariableHistorySerialized

import java.sql.{Connection, Timestamp}
import java.time.LocalDateTime

case class UpsertToDatabase(
                             connection: Connection,
                             usageContext: UsageContext,
                             logger: Logger,
                             formatter: RichInteractionVariableFormatter
                           ) {

  private lazy val generic: GenericSqlFunctionality = new GenericSqlFunctionality(connection, usageContext, logger, formatter)
  private lazy val delete: DeleteInDatabase = new DeleteInDatabase(connection, usageContext, logger, formatter)

  def findMaxTimestamp(history: InteractionVariableHistorySerialized): LocalDateTime = {
    history.states.maxBy(_.timestamp).timestamp
  }

  def upsertIntoTableWithKeys(request: StoreToDbRequest): SyncSuccess = {
    val sql =
      s"""
         |INSERT INTO `eventsWithKey` (`programid`, `scenarioid`, `userid`, `timestamp`, `serializeddata`, `eventkey`)
         |VALUES (?, ?, ?, ?, ?, ?)
         |ON DUPLICATE KEY UPDATE
         |  `timestamp` = VALUES(`timestamp`),
         |  `serializeddata` = VALUES(`serializeddata`)
         |""".stripMargin


    val stmt = connection.prepareStatement(sql)
    stmt.setString(1, request.syncContext.programId)
    stmt.setString(2, request.syncContext.scenarioId)
    stmt.setString(3, request.syncContext.userId)
    stmt.setTimestamp(4, Timestamp.valueOf(findMaxTimestamp(request.historySerialized)))
    stmt.setString(5, request.serializedValueString)
    stmt.setString(6, request.syncContext.keyForSerialisation)

    val res = generic.executeUpdate(stmt)
    println(s"Upserted $res event into database")
    SyncSuccess(0, res, 0, LocalDateTime.now())
  }

  def upsertIntoTableWithoutKeys(request: StoreToDbRequest, tableName: String = "events"): SyncSuccess = {
    val key = request.syncContext.keyForSerialisation
    delete.deleteByKeyInDatabaseWithoutKey(key, tableName)
    insertIntoTableWithoutKeys(request, tableName)
  }

  def insertIntoTableWithoutKeys(request: StoreToDbRequest, tableName: String = "events"): SyncSuccess = {
    val sql =
      s"""
         |INSERT INTO `$tableName` (`programid`, `scenarioid`, `userid`, `timestamp`, `serializeddata`)
         |VALUES (?, ?, ?, ?, ?)
         |ON DUPLICATE KEY UPDATE
         |  `timestamp` = VALUES(`timestamp`),
         |  `serializeddata` = VALUES(`serializeddata`)
         |""".stripMargin

    val stmt = connection.prepareStatement(sql)

    stmt.setString(1, request.syncContext.programId)
    stmt.setString(2, request.syncContext.scenarioId)
    stmt.setString(3, request.syncContext.userId)
    stmt.setTimestamp(4, Timestamp.valueOf(findMaxTimestamp(request.historySerialized)))
    stmt.setString(5, request.serializedValueString)

    val res = generic.executeUpdate(stmt)
    println(s"Inserted $res event into database")
    SyncSuccess(res, 0, 0, LocalDateTime.now())
  }

}

object UpsertToDatabase {

  def handleRequest(request: StoreToDbRequest, logger: Logger): SyncSuccess = {
    val config = DatabaseConfig.readFromEnv(request.databaseName)
    val connection = config.newConnection()

    val control = UpsertToDatabase(connection, request.usageContext, logger, request.formatter)

    if (request.hasDatabaseKeyColumn) control.upsertIntoTableWithKeys(request)
    else control.upsertIntoTableWithoutKeys(request, "events")

  }

}
