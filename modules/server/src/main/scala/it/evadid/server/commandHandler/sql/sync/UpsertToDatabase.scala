package it.evadid.server.commandHandler.sql.sync

import it.evadid.workbook.interaction.sync.model.HistoryJson
import it.evadid.distribution.commandTypes.SQLCommands.StoreToDbRequest
import it.evadid.server.commandHandler.sql.{GenericSqlFunctionality, RichDatabaseConfig}
import it.evadid.sql.DatabaseConfig
import it.evadid.util.logging.Logger
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.UsageContext

import java.sql.{Connection, DriverManager, PreparedStatement, Timestamp}
import java.time.LocalDateTime

case class UpsertToDatabase(
                             connection: Connection,
                             usageContext: UsageContext,
                             logger: Logger
                           ) {

  private lazy val generic: GenericSqlFunctionality = new GenericSqlFunctionality(connection, logger)
  private lazy val delete: DeleteInDatabase = new DeleteInDatabase(connection, usageContext, logger)

  def findMaxTimestamp(history: String): Option[LocalDateTime] = HistoryJson.lastTimestamp(history)

  def upsertIntoTableWithKeys(request: StoreToDbRequest): SyncSuccess =
    if (HistoryJson.lastTimestamp(request.historyJson).isEmpty) SyncSuccess(0, 0, 0, LocalDateTime.now()) else {
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
      stmt.setTimestamp(4, Timestamp.valueOf(HistoryJson.lastTimestamp(request.historyJson).get))
      stmt.setString(5, HistoryJson.stored(request.syncContext.keyForSerialisation, request.historyJson))
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
    if (HistoryJson.lastTimestamp(request.historyJson).isEmpty) SyncSuccess(0, 0, 0, LocalDateTime.now()) else {
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
      stmt.setTimestamp(4, Timestamp.valueOf(HistoryJson.lastTimestamp(request.historyJson).get))
      stmt.setString(5, HistoryJson.stored(request.syncContext.keyForSerialisation, request.historyJson))

      val res = generic.executeUpdate(stmt)
      println(s"Inserted $res event into database")
      SyncSuccess(res, 0, 0, LocalDateTime.now())
    }

  }
}

object UpsertToDatabase {




  def handleRequest(request: StoreToDbRequest, logger: Logger): SyncSuccess =
    if (HistoryJson.lastTimestamp(request.historyJson).isEmpty) SyncSuccess(0, 0, 0, LocalDateTime.now()) else {
      val config = DatabaseConfig.readFromEnv(dbName = Some(request.databaseName))
      val connection = config.newConnection()

      val control = UpsertToDatabase(connection, request.usageContext, logger)

      if (request.databaseFormat.hasKeyTable) control.upsertIntoTableWithKeys(request)
      else control.upsertIntoTableWithoutKeys(request, "events")
    }

}
