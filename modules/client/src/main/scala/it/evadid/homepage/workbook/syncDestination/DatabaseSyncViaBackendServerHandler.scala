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

  assert(definition.databaseConfig.databaseOp.nonEmpty, "database name must not be empty!")
  val dbName: String = definition.databaseConfig.databaseOp.get

  private def backend: ExecutionClient = fullInfo.defaults.backendExecutor

  private given ec: ExecutionContext = ExecutionContext.global

  /*





  override def fetchAllInteractions(logger: SyncLogger, context: UsageContext): Future[FetchResponse[SyncContext, String]] = {

  }

  override def shouldBePersistant(): Boolean = true

  override def clearAllValues(logger: SyncLogger, context: UsageContext): Future[SyncSuccess] = {
    clearValues(context, None)
  }

  override def clearValues(logger: SyncLogger, context: SyncContext): Future[SyncSuccess] = {
    clearValues(context.toUsageContext, Some(context.keyForSerialisation))
  }

  def clearValues(context: UsageContext, limitToKey: Option[String]): Future[SyncSuccess] = {
    val request = SQLCommands.DeleteInDbRequest(context, limitToKey, dbName, definition.format)
    val exInfoFut: Future[ExecutionSuccess[SyncSuccess]] = SQLCommands.clearValuesDbCommand.sendCommandTo(backend, request, None, None)
    exInfoFut.map(_.result)(using ec)
  }

  override def isLocal: Boolean = false

*/

  override def syncLogger: SyncLogger = pSyncLogger

  private def clearValues(context: UsageContext, limitToKey: Option[String]): Future[SyncSuccess] = {
    val request = SQLCommands.DeleteInDbRequest(context, limitToKey, dbName, definition.format)
    val exInfoFut: Future[ExecutionSuccess[SyncSuccess]] = SQLCommands.clearValuesDbCommand.sendCommandTo(backend, request, None, None)
    exInfoFut.map(_.result)(using ec)
  }

  override def clearValue(context: SyncContext)(implicit ec: ExecutionContext): Future[SyncSuccess] = {
    clearValues(context.toUsageContext, Some(context.keyForSerialisation))
  }

  override def clearValues(context: UsageContext)(implicit ec: ExecutionContext): Future[SyncSuccess] = clearValues(context, None)

  override def storeHistoryJson(syncContext: SyncContext, historyJson: String)(implicit ec: ExecutionContext): Future[SyncSuccess] = {
    val dbRequest = StoreToDbRequest(syncContext, historyJson, definition.databaseConfig.databaseOp.get, definition.format)
    val exInfo: Future[ExecutionSuccess[SyncSuccess]] = SQLCommands.StoreToDbCommand.sendCommandTo(backend, dbRequest)
    exInfo.map(exInfo => exInfo.result)(using ec)
  }

  private def toFetchResponse(executionInfoTyped: ExecutionSuccess[DbFetchResponse]): FetchResponse[SyncContext, String] = {
    FetchResponse.fromMap[SyncContext, String](executionInfoTyped.history.timestampExecutionFinished, executionInfoTyped.result.fetchedElements, HistoryJson.lastTimestamp)
  }

  override def readHistoriesJson(usageContext: UsageContext)(implicit ec: ExecutionContext): Future[FetchResponse[SyncContext, String]] = {
    val request = SQLCommands.FetchAllFromDbRequest(usageContext, dbName, None, definition.format)
    val exInfoFut: Future[ExecutionSuccess[DbFetchResponse]] = SQLCommands.fetchFromDbCommand.sendCommandTo(backend, request)
    exInfoFut.map(toFetchResponse)
  }
}
