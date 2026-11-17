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
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson

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

  override def clearValue(context: SyncContext)(implicit ec: ExecutionContext): Future[SyncSuccess] = {
    clearValues(context.toUsageContext, Some(context.keyForSerialisation))
  }

  override def clearValues(context: UsageContext)(implicit ec: ExecutionContext): Future[SyncSuccess] = clearValues(context, None)


  override def storeHistoryJson(syncContext: SyncContext, historyJson: String)(implicit ec: ExecutionContext): Future[SyncSuccess] = {
    // Look up format from the interaction definition using syncContext
    val format = lookupFormatFromSyncContext(syncContext)
    val history = InteractionHistoryJson(format, historyJson)
    val dbRequest = StoreToDbRequest(syncContext, history, databaseConfig, definition.format)
    val exInfo: Future[ExecutionSuccess[SyncSuccess]] = SQLCommands.StoreToDbCommand.sendCommandTo(backend, dbRequest)
    exInfo.map(exInfo => exInfo.result)(using ec)
  }

  private def lookupFormatFromSyncContext(syncContext: SyncContext): InteractionFormat[?, ?, ?] = {
    // Look up the format from the interaction definition using syncContext.keyForSerialisation
    // This is a placeholder - in practice you'd look up from the interaction registry
    import it.evadid.workbook.abstractions.InteractionFormat
    syncContext.keyForSerialisation match {
      case "TextInteraction_history" => TextInteractionFormat
      case "LabeledNumberInteraction_history" => LabeledNumberInteractionFormat
      case "LabeledCheckboxInteraction_history" => LabeledCheckboxInteractionFormat
      case "MessagingInteraction_history" => MessagingInteractionFormat
      case _ => TextInteractionFormat  // default fallback
    }
  }

  private def toFetchResponse(executionInfoTyped: ExecutionSuccess[DbFetchResponse]): FetchResponse[SyncContext, InteractionHistoryJson[_ <: InteractionFormat]] = {
    FetchResponse.fromMap[SyncContext, InteractionHistoryJson[_ <: InteractionFormat]](executionInfoTyped.history.timestampExecutionFinished, executionInfoTyped.result.fetchedElements)
  }

  override def readHistoriesJson(usageContext: UsageContext)(implicit ec: ExecutionContext): Future[FetchResponse[SyncContext, InteractionHistoryJson[_ <: InteractionFormat]]] = {
    val request = SQLCommands.FetchAllFromDbRequest(usageContext, databaseConfig, None, definition.format)
    val exInfoFut: Future[ExecutionSuccess[DbFetchResponse]] = SQLCommands.fetchFromDbCommand.sendCommandTo(backend, request)
    exInfoFut.map(toFetchResponse)
  }
}
