package it.evadid.homepage.workbook.syncDestination

import it.evadid.core.util.io.ConstructorLikeParserWithJsonElements
import it.evadid.core.util.io.ConstructorLikeParserWithJsonElements.ConstructorLikeReadResult
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.ExecutionInfo.ExecutionSuccess
import it.evadid.distribution.commandTypes.SQLCommands
import it.evadid.distribution.commandTypes.SQLCommands.{DbFetchResponse, StoreToDbRequest}
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.destination.*
import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.SyncDestinationBasedOnHistoryDatabase
import it.evadid.workbook.interaction.sync.model.{FetchResponse, HistoryJson}
import jdk.jpackage.internal.Arguments.CLIOptions.context
import org.scalajs.dom.window.history

import scala.concurrent.*

case class DatabaseSyncViaBackendServerHandler(pSyncLogger: SyncLogger, definition: SyncDestinationBasedOnHistoryDatabase) extends SyncDestinationHistoryHandler {

  private def backend: ExecutionClient = fullInfo.defaults.backendExecutor
  private given ec: ExecutionContext = ExecutionContext.global
  private def connectionInfo = definition.databaseConfig.withFallback(fullInfo.defaults.defaultDb)
  private def credentials = fullInfo.homepageInfoNow().userInfo.flatMap(_.credentials.databaseConfig)

  override def syncLogger: SyncLogger = pSyncLogger

  private def clearValues(context: UsageContext, limitToKey: Option[String]): Future[SyncSuccess] = {
    val request = SQLCommands.DeleteInDbRequest(context, limitToKey, connectionInfo, definition.format, credentials)
    val exInfoFut: Future[ExecutionSuccess[SyncSuccess]] = SQLCommands.clearValuesDbCommand.sendCommandTo(backend, request, None, None)
    exInfoFut.map(_.result)(using ec)
  }

  override def clearValue(context: SyncContext)(implicit ec: ExecutionContext): Future[SyncSuccess] = {
    clearValues(context.toUsageContext, Some(context.keyForSerialisation))
  }

  override def clearValues(context: UsageContext)(implicit ec: ExecutionContext): Future[SyncSuccess] = clearValues(context, None)

  override def storeHistoryJson(syncContext: SyncContext, historyJson: String)(implicit ec: ExecutionContext): Future[SyncSuccess] = {
    val dbRequest = StoreToDbRequest(syncContext, historyJson, connectionInfo, definition.format, credentials)
    val exInfo: Future[ExecutionSuccess[SyncSuccess]] = SQLCommands.StoreToDbCommand.sendCommandTo(backend, dbRequest)
    exInfo.map(exInfo => exInfo.result)(using ec)
  }

  private def toFetchResponse(executionInfoTyped: ExecutionSuccess[DbFetchResponse]): FetchResponse[SyncContext, String] = {
    FetchResponse.fromMap[SyncContext, String](executionInfoTyped.history.timestampExecutionFinished, executionInfoTyped.result.fetchedElements, HistoryJson.lastTimestamp)
  }

  override def readHistoriesJson(usageContext: UsageContext)(implicit ec: ExecutionContext): Future[FetchResponse[SyncContext, String]] = {
    val request = SQLCommands.FetchAllFromDbRequest(usageContext, connectionInfo, None, definition.format, credentials)
    val exInfoFut: Future[ExecutionSuccess[DbFetchResponse]] = SQLCommands.fetchFromDbCommand.sendCommandTo(backend, request)
    exInfoFut.map(toFetchResponse)
  }
}
