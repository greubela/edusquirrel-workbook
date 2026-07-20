package it.evadid.homepage.workbook.syncDestination

import it.evadid.core.datastructures.storage.RemoteSyncDataCache
import it.evadid.core.datastructures.storage.RemoteSyncDataCache.FetchResponse
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.ExecutionInfo.ExecutionInfoTyped
import it.evadid.distribution.commandTypes.SQLCommands
import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.SyncInformation.SyncSuccess
import it.evadid.workbook.interaction.sync.{SyncContext, SyncDestination, SyncFormatter, UsageContext}
import it.evadid.workbook.interaction.variable.InteractionVariableHistorySerialized

import scala.concurrent.*

case class DatabaseSyncViaBackendServer(dbName: String, hasKeyTable: Boolean) extends SyncDestination {

  override val toString: String = "DatabaseSyncViaBackendServer(" + dbName + ", " + hasKeyTable + ")"

  private lazy val backend: ExecutionClient = HtmlFullWorkbookApp.fullInfo.defaults.defaultBackend.executor

  private given ec: ExecutionContext = ExecutionContext.global

  override def storeTo(logger: SyncLogger, context: SyncContext, history: InteractionVariableHistorySerialized, formatter: SyncFormatter): Future[SyncSuccess] = {
    val dbRequest = StoreToDbRequest(context, history, dbName, hasKeyTable)
    val exInfo: Future[ExecutionInfoTyped[SyncSuccess]] = SQLCommands.StoreToDbCommand.sendCommandTo(backend, dbRequest)
    exInfo.map(exInfo => exInfo.resultTyped.result)(using ec)
  }

  private def toFetchResponse(executionInfoTyped: ExecutionInfoTyped[DbFetchResponse]): FetchResponse[SyncContext, InteractionVariableHistorySerialized] = {
    FetchResponse.fromMap[SyncContext, InteractionVariableHistorySerialized](executionInfoTyped.history.timestampExecutionFinished, executionInfoTyped.resultTyped.result.fetchedElements, _.lastStateOption.map(_.timestamp))
  }

  override def fetchAll(logger: SyncLogger, context: UsageContext, formatter: SyncFormatter): Future[RemoteSyncDataCache.FetchResponse[SyncContext, InteractionVariableHistorySerialized]] = {
    val request = SQLCommands.FetchAllFromDbRequest(context, dbName, None, hasKeyTable)
    val exInfoFut: Future[ExecutionInfoTyped[DbFetchResponse]] = SQLCommands.fetchFromDbCommand.sendCommandTo(backend, request)
    exInfoFut.map(toFetchResponse)
  }


  override def shouldBePersistant(): Boolean = true

  override def clearAllValues(logger: SyncLogger, context: UsageContext): Future[SyncSuccess] = {
    clearValues(context, None)
  }

  override def clearValues(logger: SyncLogger, context: SyncContext): Future[SyncSuccess] = {
    clearValues(context.toUsageContext, Some(context.keyForSerialisation))
  }

  def clearValues(context: UsageContext, limitToKey: Option[String]): Future[SyncSuccess] = {
    val request = SQLCommands.DeleteInDbRequest(context, limitToKey, dbName, hasKeyTable)
    val exInfoFut: Future[ExecutionInfoTyped[SyncSuccess]] = SQLCommands.clearValuesDbCommand.sendCommandTo(backend, request, None, None)
    exInfoFut.map(_.resultTyped.result)(using ec)
  }

  override def isLocal: Boolean = false
}
