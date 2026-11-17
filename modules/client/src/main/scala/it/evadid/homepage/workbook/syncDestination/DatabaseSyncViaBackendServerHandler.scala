package it.evadid.homepage.workbook.syncDestination

import it.evadid.core.util.io.ConstructorLikeParserWithJsonElements
import it.evadid.core.util.io.ConstructorLikeParserWithJsonElements.ConstructorLikeReadResult
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.ExecutionInfo.ExecutionSuccess
import it.evadid.distribution.commandTypes.SQLCommands
import it.evadid.distribution.commandTypes.SQLCommands.{DbFetchResponse, StoreToDbRequest}
import it.evadid.homepage.control.model.FullInfo
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.destination.*
import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.SyncDestinationBasedOnHistoryDatabase
import it.evadid.workbook.interaction.sync.model.{FetchResponse, InteractionHistoryJson}

import scala.concurrent.*

case class DatabaseSyncViaBackendServerHandler(pSyncLogger: SyncLogger, definition: SyncDestinationBasedOnHistoryDatabase, fullInfo: FullInfo) extends SyncDestinationHistoryHandler {

  private val backend: ExecutionClient = fullInfo.defaults.backendExecutor
  private given ec: ExecutionContext = ExecutionContext.global
  private val databaseConfig: it.evadid.sql.DatabaseConfiguration =
    fullInfo.homepageInfoNow().userInfo.flatMap(_.credentials.databaseConfig).getOrElse(definition.databaseConfig)

  override def syncLogger: SyncLogger = pSyncLogger

  private def clearValues(context: UsageContext, limitToKey: Option[String]): Future[SyncSuccess] = {
    val request = SQLCommands.DeleteInDbRequest(context, limitToKey, databaseConfig, definition.format)
    val exInfoFut: Future[ExecutionSuccess[SyncSuccess]] = SQLCommands.clearValuesDbCommand.sendCommandTo(backend, request, None, None)
    exInfoFut.map(_.result)(using ec)
  }

  override def clearAllHistories(context: UsageContext)(using ExecutionContext): Future[SyncSuccess] = {
    val request = SQLCommands.DeleteInDbRequest(context, None, databaseConfig, definition.format, allWorkbooks = true)
    SQLCommands.resetUserHistoriesDbCommand.sendCommandTo(backend, request).map(_.result)
  }

  override def clearValue(context: SyncContext)(implicit ec: ExecutionContext): Future[SyncSuccess] = {
    clearValues(context.toUsageContext, Some(context.keyForSerialisation))
  }

  override def clearValues(context: UsageContext)(implicit ec: ExecutionContext): Future[SyncSuccess] = clearValues(context, None)


  override def storeHistoryJson(context: SyncContext, json: String)(using ExecutionContext): Future[SyncSuccess] =
    storeHistoriesJson(Map(context -> json))

  override def storeHistoriesJson(values: Map[SyncContext, String])(using ExecutionContext): Future[SyncSuccess] = {
    if values.isEmpty then Future.successful(SyncSuccess.emptyNow()) else {
      val entries = values.toSeq.map((context, json) => context -> InteractionHistoryJson.deserialize(json))
      val request = SQLCommands.BulkStoreToDbRequest(entries.map(_._1), entries.map(_._2), databaseConfig, definition.format)
      SQLCommands.bulkStoreToDbCommand.sendCommandTo(backend, request).map(_.result)
    }
  }

  private def toFetchResponse(result: ExecutionSuccess[DbFetchResponse]): FetchResponse[SyncContext, String] =
    FetchResponse.fromMap(result.history.timestampExecutionFinished, result.result.fetchedElements.map((key, value) => key -> value.serialized))

  override def readHistoriesJson(usageContext: UsageContext)(using ec: ExecutionContext): Future[FetchResponse[SyncContext, String]] = {
    val request = SQLCommands.FetchEventsFromDbRequest(usageContext, Seq.empty, java.time.LocalDateTime.MIN,
      java.time.LocalDateTime.MAX, databaseConfig, definition.format, EventSyncStrategy.SYNC_EVERYTHING)
    SQLCommands.fetchEventsFromDbCommand.sendCommandTo(backend, request).map(toFetchResponse)
  }

  override def readHistoriesInRange(usageContext: UsageContext, keys: Seq[SyncContext], starting: java.time.LocalDateTime,
      until: java.time.LocalDateTime, strategy: EventSyncStrategy)(using ec: ExecutionContext): Future[Map[SyncContext, InteractionHistoryJson]] = {
    require(keys.forall(_.toUsageContext == usageContext), "Range keys must belong to the requested usage context")
    require(!until.isBefore(starting), "History range end must not precede its start")
    if keys.isEmpty then Future.successful(Map.empty) else {
      val request = SQLCommands.FetchEventsFromDbRequest(usageContext, keys.map(_.keyForSerialisation), starting, until,
        databaseConfig, definition.format, strategy)
      SQLCommands.fetchEventsFromDbCommand.sendCommandTo(backend, request).map(_.result.fetchedElements)
    }
  }
}
