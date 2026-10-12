package it.evadid.homepage.workbook.syncDestination

import it.evadid.core.util.io.ConstructorLikeParserWithJsonElements
import it.evadid.core.util.io.ConstructorLikeParserWithJsonElements.ConstructorLikeReadResult
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.ExecutionInfo.ExecutionSuccess
import it.evadid.distribution.commandTypes.SQLCommands
import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.destination.*
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.model.{FetchResponse, HistoryJson}

import scala.concurrent.*

case class DatabaseSyncViaBackendServer(dbName: String, hasKeyTable: Boolean) extends SyncDestinationHistory {

  override val toString: String = "DatabaseSyncViaBackendServer(" + dbName + ", " + hasKeyTable + ")"

  private def backend: ExecutionClient = HtmlFullWorkbookApp.fullInfo.defaults.backendExecutor

  private given ec: ExecutionContext = ExecutionContext.global

  override def storeTo(logger: SyncLogger, context: SyncContext, history: String): Future[SyncSuccess] = {
    val dbRequest = StoreToDbRequest(context, history, dbName, hasKeyTable)
    val exInfo: Future[ExecutionSuccess[SyncSuccess]] = SQLCommands.StoreToDbCommand.sendCommandTo(backend, dbRequest)
    exInfo.map(exInfo => exInfo.result)(using ec)
  }

  private def toFetchResponse(executionInfoTyped: ExecutionSuccess[DbFetchResponse]): FetchResponse[SyncContext, String] = {
    FetchResponse.fromMap[SyncContext, String](executionInfoTyped.history.timestampExecutionFinished, executionInfoTyped.result.fetchedElements, HistoryJson.lastTimestamp)
  }

  override def fetchAll(logger: SyncLogger, context: UsageContext): Future[FetchResponse[SyncContext, String]] = {
    val request = SQLCommands.FetchAllFromDbRequest(context, dbName, None, hasKeyTable)
    val exInfoFut: Future[ExecutionSuccess[DbFetchResponse]] = SQLCommands.fetchFromDbCommand.sendCommandTo(backend, request)
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
    val exInfoFut: Future[ExecutionSuccess[SyncSuccess]] = SQLCommands.clearValuesDbCommand.sendCommandTo(backend, request, None, None)
    exInfoFut.map(_.result)(using ec)
  }

  override def isLocal: Boolean = false


  override protected def deserializeFromConstructorLikeString(from: ConstructorLikeParserWithJsonElements.ConstructorLikeReadResult): Option[SyncDestination] = {
    if (from.elementType == this.getClass.getSimpleName) Some(DatabaseSyncViaBackendServer(from.jsonPayloads(0), from.jsonPayloads(1).toBoolean))
    else None
  }

  override protected def serializeToConstructorLikeString(): ConstructorLikeParserWithJsonElements.ConstructorLikeReadResult = {
    ConstructorLikeReadResult(this.getClass.getSimpleName, List(dbName, hasKeyTable.toString))
  }
}
