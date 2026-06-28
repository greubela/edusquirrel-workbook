package it.evadid.homepage.workbook.syncDestination

import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.ExecutionInfo.ExecutionInfoTyped
import it.evadid.distribution.commandTypes.SQLCommands
import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.workbook.model.interaction.sync.*
import it.evadid.workbook.model.interaction.sync.SyncInformation.SyncSuccess
import it.evadid.workbook.model.interaction.variable.InteractionVariableHistorySerialized

import scala.concurrent.*

case class DatabaseSyncViaBackendServer(dbName: String, hasKeyTable: Boolean) extends SyncDestination {

  private lazy val backend: ExecutionClient = HtmlFullWorkbookApp.fullInfo.technical.backendServerExecutor

  private given ec: ExecutionContext = ExecutionContext.global

  override def storeTo(context: SyncContext, history: InteractionVariableHistorySerialized, formatter: SyncFormatter): Future[SyncSuccess] = {
    val dbRequest = StoreToDbRequest(context, history, dbName, hasKeyTable)
    val exInfo: Future[ExecutionInfoTyped[SyncSuccess]] = SQLCommands.StoreToDbCommand.sendCommandTo(backend, dbRequest)
    exInfo.map(exInfo => exInfo.resultTyped.result)(using ec)
  }

  override def fetchAll(context: UsageContext): Future[Map[SyncContext, String]] = {
    val request = SQLCommands.FetchAllFromDbRequest(context, dbName, None, hasKeyTable)
    val exInfoFut: Future[ExecutionInfoTyped[DbFetchResponse]] = SQLCommands.fetchFromDbCommand.sendCommandTo(backend, request)

    def serializeBack(context: SyncContext, interactionVariableHistorySerialized: InteractionVariableHistorySerialized): String = {
      request.formatter.serialize(context, interactionVariableHistorySerialized)
    }

    val res: Future[Map[SyncContext, String]] = exInfoFut.map(res => {
      val resMap: Map[SyncContext, InteractionVariableHistorySerialized] = res.resultTyped.result.fetchedElements
      resMap.iterator.map(tup => tup._1 -> serializeBack(tup._1, tup._2)).toMap
    })(using ec)

    res
  }

  override def shouldBePersistant(): Boolean = true

  override def clearAllValues(context: UsageContext): Future[SyncSuccess] = {
    clearValues(context, None)
  }

  override def clearValues(context: SyncContext): Future[SyncSuccess] = {
    clearValues(context.toUsageContext, Some(context.keyForSerialisation))
  }

  def clearValues(context: UsageContext, limitToKey: Option[String]): Future[SyncSuccess] = {
    val request = SQLCommands.DeleteInDbRequest(context, limitToKey, dbName, hasKeyTable)
    val exInfoFut: Future[ExecutionInfoTyped[SyncSuccess]] = SQLCommands.clearValuesDbCommand.sendCommandTo(backend, request, None, None)
    exInfoFut.map(_.resultTyped.result)(using ec)
  }

}
