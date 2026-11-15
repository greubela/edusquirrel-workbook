package it.evadid.workbook.interaction.sync.destination

import it.evadid.core.datastructures.state.State
import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.SerializedException
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.destination.BufferedSyncDestinationHandler.*
import it.evadid.workbook.interaction.sync.model.FetchResponse
import it.evadid.workbook.interaction.sync.{SyncContext, SyncSuccess, UsageContext}
import it.evadid.workbook.interaction.variable.*
import upickle.default.*

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.*

case class BufferedSyncDestinationHandler(underlying: SyncDestinationHistoryHandler) extends SyncDestinationHistoryHandler {


  override def syncLogger: SyncLogger = underlying.syncLogger

  override def definition: SyncDestinationDefinition = underlying.definition

  // clear
  override def clearValue(context: SyncContext)(implicit ec: ExecutionContext): Future[SyncSuccess] = underlying.clearValue(context)

  override def clearValues(context: UsageContext)(implicit ec: ExecutionContext): Future[SyncSuccess] = underlying.clearValues(context)


  // read
  override def readHistory[T: ReadWriter, GA: ReadWriter, SA: ReadWriter](syncContext: SyncContext)(implicit ec: ExecutionContext): Future[InteractionVariableHistory[T, GA, SA]] = {
    val promise = Promise[InteractionVariableHistory[T, GA, SA]]()
    cachedReads.update(_ ++ List(CachedReadRequestSingle(LocalDateTime.now(), syncContext, promise)))
    flush(false)
    promise.future
  }

  override def readHistories(usageContext: UsageContext, variablesToResolveTypes: Seq[InteractionVariable[?, ?, ?]])(implicit ec: ExecutionContext): Future[Map[SyncContext, InteractionVariableHistory[?, ?, ?]]] = {
    val promise = Promise[Map[SyncContext, InteractionVariableHistory[?, ?, ?]]]()
    cachedReads.update(_ ++ List(CachedReadRequestMultiple(LocalDateTime.now(), usageContext, variablesToResolveTypes, promise)))
    flush(false)
    promise.future
  }

  override def readHistoriesJson(usageContext: UsageContext)(implicit ec: ExecutionContext): Future[FetchResponse[SyncContext, String]] = {
    val promise = Promise[FetchResponse[SyncContext, String]]()
    cachedReads.update(_ ++ List(CachedReadRequestMultipleRaw(LocalDateTime.now(), usageContext, promise)))
    flush(false)
    promise.future
  }

  // store

  val cachedStores: State[Seq[CachedStoreRequest]] = State(List())
  val cachedReads: State[Seq[CachedReadRequest]] = State(List())

  override def storeHistoryJson(syncContext: SyncContext, historyJson: String)(implicit ec: ExecutionContext): Future[SyncSuccess] = this.synchronized {
    val promise = Promise[SyncSuccess]()
    cachedStores.update(_ ++ List(CachedStoreRequest(LocalDateTime.now(), Map(syncContext -> historyJson), promise)))
    flush(false)
    promise.future
  }

  override def storeHistoriesJson(contextJsonMap: Map[SyncContext, String])(implicit ec: ExecutionContext): Future[SyncSuccess] = this.synchronized {
    val promise = Promise[SyncSuccess]()
    cachedStores.update(_ ++ List(CachedStoreRequest(LocalDateTime.now(), contextJsonMap, promise)))
    flush(false)
    promise.future
  }


  def timestamps: Set[LocalDateTime] = this.synchronized {
    (cachedReads.now().map(_.requestTimestamp) ++ cachedStores.now().map(_.requestTimestamp)).toSet
  }

  private def shouldFlush(): Boolean = this.synchronized {
    if (timestamps.isEmpty) false
    else if (cachedReads.now().size + cachedStores.now().size > 30) true
    else if (ChronoUnit.SECONDS.between(timestamps.min, LocalDateTime.now()) > 30) true
    else false
  }

  override def flush(forceFlush: Boolean = true): Unit = this.synchronized {
    if (forceFlush || shouldFlush()) {
      BufferedSyncDestinationHandler.handleStores(cachedStores.now(), underlying)
      cachedStores.set(List())

      BufferedSyncDestinationHandler.handleReads(cachedReads.now(), underlying)
      cachedReads.set(List())
    }
  }


}

object BufferedSyncDestinationHandler {

  private given ec: ExecutionContext = ExecutionContext.global

  private def handleReads(cachedReads: Seq[CachedReadRequest], underlying: SyncDestinationHistoryHandler): Unit = {
    if (cachedReads.nonEmpty) {
      val fut = Future.traverse(cachedReads.map(_.usageContext).toSet)(curContext => underlying.readHistoriesJson(curContext))
        .map(readResults => readResults.fold(readResults.head)(_.combineWith(_)))
        .onComplete(curTry => cachedReads.foreach(_.fulfillWith(curTry))
        )
    }
  }

  private def handleStores(cachedStores: Seq[CachedStoreRequest], underlying: SyncDestinationHistoryHandler): Unit = {
    if (cachedStores.nonEmpty) {
      val allSyncContexts: Set[SyncContext] = cachedStores.flatMap(_.toStore.keySet).toSet
      val toStoreMap: Map[SyncContext, String] = allSyncContexts.map(curKey => {
        curKey -> cachedStores.filter(_.toStore.contains(curKey)).maxBy(_.requestTimestamp).toStore(curKey)
      }).toMap
      underlying.storeHistoriesJson(toStoreMap).onComplete(curTry => cachedStores.foreach(_.promise.tryComplete(curTry)))

    }
  }

  sealed trait CachedRequest {
    def requestTimestamp: LocalDateTime
  }

  case class CachedStoreRequest(requestTimestamp: LocalDateTime, toStore: Map[SyncContext, String], promise: Promise[SyncSuccess])

  sealed trait CachedReadRequest extends CachedRequest {
    def fulfillWith(readData: Try[FetchResponse[SyncContext, String]]): Unit

    def usageContext: UsageContext
  }

  case class CachedReadRequestSingle[T: ReadWriter, GA: ReadWriter, SA: ReadWriter](requestTimestamp: LocalDateTime, syncContext: SyncContext, promise: Promise[InteractionVariableHistory[T, GA, SA]]) extends CachedReadRequest {

    override def fulfillWith(readData: Try[FetchResponse[SyncContext, String]]): Unit = readData.match {
      case Success(data) => {
        val serializer = Serializer.fromUpickleJson(InteractionVariableHistory.given_ReadWriter_InteractionVariableHistory[T, GA, SA])
        val history: Either[SerializedException, InteractionVariableHistory[T, GA, SA]] = SyncDestinationHistoryHandler.readHistoryFrom(serializer, syncContext, data)
        promise.tryComplete(history.toTry)
      }
      case Failure(err) => promise.tryFailure(err)
    }

    override def usageContext: UsageContext = syncContext.toUsageContext

  }

  case class CachedReadRequestMultiple(requestTimestamp: LocalDateTime, usageContext: UsageContext, resolveWithVars: Seq[InteractionVariable[?, ?, ?]], promise: Promise[Map[SyncContext, InteractionVariableHistory[?, ?, ?]]]) extends CachedReadRequest {

    override def fulfillWith(readData: Try[FetchResponse[SyncContext, String]]): Unit = readData.match {
      case Success(data) => {
        val histories = SyncDestinationHistoryHandler.readHistoriesFrom(resolveWithVars, usageContext, data)
        promise.trySuccess(histories)
      }
      case Failure(err) => promise.tryFailure(err)
    }

  }

  case class CachedReadRequestMultipleRaw(requestTimestamp: LocalDateTime, usageContext: UsageContext, promise: Promise[FetchResponse[SyncContext, String]]) extends CachedReadRequest {

    override def fulfillWith(readData: Try[FetchResponse[SyncContext, String]]): Unit = promise.tryComplete(readData)

  }

}



