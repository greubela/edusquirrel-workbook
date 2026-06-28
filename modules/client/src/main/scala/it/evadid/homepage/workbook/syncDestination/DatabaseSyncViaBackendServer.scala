package it.evadid.homepage.workbook.syncDestination

import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.ExecutionInfo.ExecutionInfoTyped
import it.evadid.distribution.commandTypes.SQLCommands
import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.homepage.control.HtmlFullWorkbookApp
import it.evadid.util.Logger
import it.evadid.workbook.model.interaction.sync.SyncFormatter.InteractionSyncRequest
import it.evadid.workbook.model.interaction.sync.SyncInformation.SyncSuccess
import it.evadid.workbook.model.interaction.sync.*

import scala.concurrent.*
import scala.util.*

case class DatabaseSyncViaBackendServer(dbName: String) extends SyncDestination {

  private lazy val backend: ExecutionClient = HtmlFullWorkbookApp.fullInfo.technical.backendServerExecutor

  private given ec: ExecutionContext = ExecutionContext.global

  override def syncTo(context: SyncContext, request: InteractionSyncRequest, formatter: SyncFormatter): Future[SyncSuccess] = {
    val dbRequest = StoreToDbRequest(request, dbName)
    val exInfo: Future[ExecutionInfoTyped[SyncSuccess]] = SQLCommands.syncToDbCommand.sendCommandTo(backend, Logger(), dbRequest)
    exInfo.map(exInfo => exInfo.resultTyped.result)(using ec)
  }

  override def fetchAll(context: UsageContext): Future[Map[SyncContext, String]] = {

    println("########################## DbSync: Fetching from db, context: " + context)

    val serializer = DefaultSerializer.serializerInteractionVariableHistoryIgnoreErrors
    val request = SQLCommands.FetchFromDbRequest(context, dbName)
    val exInfoFut: Future[ExecutionInfoTyped[DbFetchResponse]] = SQLCommands.fetchFromDbCommand.sendCommandTo(backend, Logger(), request)
    exInfoFut.onComplete {
      case Success(exInfo) => println("DbSync: " + exInfo.resultTyped.result.fetchedElements)
      case Failure(err) => println("DbSync: " + err)
    }

    val res = exInfoFut.map(exInfo => {
      exInfo.resultTyped.result.fetchedElements.map(tup => tup._1 -> serializer.serialize(tup._2)).toMap
    })(using ec)
    //exInfoFut.map(exInfo => .serialize(exInfo.resultTyped.result.fetchedElements))(using ec)

    res
  }

  override def shouldBePersistant(): Boolean = true

  override def clearAllValues(context: UsageContext): Future[SyncSuccess] = {
    val promise: Promise[SyncSuccess] = Promise()
    val request = SQLCommands.ClearUsageInDbRequest(context, None, dbName)
    val exInfoFut: Future[ExecutionInfoTyped[SyncSuccess]] = SQLCommands.clearValuesDbCommand.sendCommandTo(backend, Logger(), request)
    exInfoFut.map(_.resultTyped.result)(using ec)
  }

  override def clearValues(context: SyncContext): Future[SyncSuccess] = {
    val promise: Promise[SyncSuccess] = Promise()
    val request = SQLCommands.ClearUsageInDbRequest(context.toUsageContext, Some(context.keyForSerialisation), dbName)
    val exInfoFut: Future[ExecutionInfoTyped[SyncSuccess]] = SQLCommands.clearValuesDbCommand.sendCommandTo(backend, Logger(), request)
    exInfoFut.map(_.resultTyped.result)(using ec)
  }
}

/*

private val cache: mutable.Map[String, String] = mutable.Map.empty


private def getSyncContext(key: String): SyncContext = HtmlFullWorkbookApp.fullInfo.current.currentHomepageContext.toSyncContext(key)

override def syncTo(key: String, value: String): Unit = {
  cache.update(key, value)

  val backend: ExecutionClient = HtmlFullWorkbookApp.fullInfo.technical.backendServerExecutor


}

override def syncAllFrom(): Map[String, String] = {
  fetchFromBackend(None)
  cache.toMap
}

override def syncKeyFrom(key: String): Option[String] = {
  fetchFromBackend(Some(key))
  cache.get(key)
}

override def clearValues(key: String): Unit = {
  cache.clear()

}

private def fetchFromBackend(key: Option[String]): Unit = {
  val context = getSyncContext(key)


  }
}

private def serializedSyncEvent(key: String, value: String): String =
  ujson.Obj(
    "type" -> "syncEvent",
    "name" -> "syncInfo",
    "source" -> "DatabaseSync",
    "key" -> key,
    "data" -> value
  ).render()

*/

