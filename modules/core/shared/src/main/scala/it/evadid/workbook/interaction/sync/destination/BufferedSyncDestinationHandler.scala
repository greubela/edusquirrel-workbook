package it.evadid.workbook.interaction.sync.destination

import it.evadid.core.datastructures.state.State
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.interaction.sync.destination.BufferedSyncDestinationHandler.*
import it.evadid.workbook.interaction.sync.model.{FetchResponse, InteractionHistoryJson}
import it.evadid.workbook.interaction.sync.{EventSyncStrategy, SyncContext, SyncSuccess, UsageContext}
import it.evadid.workbook.interaction.variable.*
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.InteractionFormat
import upickle.default.*

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Success, Try}

case class BufferedSyncDestinationHandler(underlying: SyncDestinationHistoryHandler) extends SyncDestinationHistoryHandler {
  override def syncLogger: SyncLogger = underlying.syncLogger
  override def definition: SyncDestinationDefinition = underlying.definition
  private val cachedStores = State(Vector.empty[CachedStoreRequest])
  private val cachedReads = State(Vector.empty[CachedReadRequest])
  // Batches are queued only here, where all requests are already registered and can be flushed together.
  private val lastFlush = State(Future.unit)
  private given ExecutionContext = ExecutionContext.global

  private def enqueue[A](request: CachedReadRequest, promise: Promise[A]): Future[A] = synchronized {
    cachedReads.update(_ :+ request)
    flush(false)
    promise.future
  }
  override def readHistoriesJson(context: UsageContext)(implicit ec: ExecutionContext): Future[FetchResponse[SyncContext, String]] = {
    val promise = Promise[FetchResponse[SyncContext, String]]()
    enqueue(CachedReadRequestMultipleRaw(LocalDateTime.now(), context, promise), promise)
  }
  override def readHistoryJson(context: SyncContext)(implicit ec: ExecutionContext): Future[FetchResponse[SyncContext, String]] =
    readHistoriesJson(context.toUsageContext).map(response => response.copy(fetchedValues = response.fetchedValues.filter(_.dataKey == context)))

  override def readHistoriesInRange(context: UsageContext, keys: Seq[SyncContext], starting: LocalDateTime,
      until: LocalDateTime, strategy: EventSyncStrategy)(using ec: ExecutionContext): Future[Map[SyncContext, InteractionHistoryJson]] = {
    require(!until.isBefore(starting), "History range end must not precede its start")
    require(keys.forall(_.toUsageContext == context), "Range keys must belong to the requested usage context")
    if keys.isEmpty then Future.successful(Map.empty) else {
      val promise = Promise[FetchResponse[SyncContext, String]]()
      enqueue(CachedRangeRead(LocalDateTime.now(), context, keys.toSet, Range(starting, until, strategy), promise), promise)
        .map(_.asMap.filter((key, _) => keys.contains(key)).map((key, json) => key -> InteractionHistoryJson.deserialize(json)))
    }
  }

  override def storeHistoryJson(context: SyncContext, json: String)(implicit ec: ExecutionContext): Future[SyncSuccess] = storeHistoriesJson(Map(context -> json))
  override def storeHistoriesJson(values: Map[SyncContext, String])(implicit ec: ExecutionContext): Future[SyncSuccess] = synchronized {
    if values.isEmpty then Future.successful(SyncSuccess.emptyNow()) else {
      val promise = Promise[SyncSuccess]()
      cachedStores.update(_ :+ CachedStoreRequest(LocalDateTime.now(), values, promise))
      flush(false)
      promise.future
    }
  }

  def timestamps: Set[LocalDateTime] = synchronized {
    (cachedReads.now().map(_.requestTimestamp) ++ cachedStores.now().map(_.requestTimestamp)).toSet
  }
  private def shouldFlush: Boolean = timestamps.minOption.exists(time =>
    ChronoUnit.SECONDS.between(time, LocalDateTime.now()) >= 30) || cachedReads.now().size + cachedStores.now().size >= 30

  override def flush(forceFlush: Boolean = true): Unit = synchronized {
    if forceFlush || shouldFlush then {
      val stores = cachedStores.now()
      val reads = cachedReads.now()
      // Detach before calling the handler: callbacks may register another request.
      cachedStores.set(Vector.empty)
      cachedReads.set(Vector.empty)
      if stores.nonEmpty || reads.nonEmpty then {
        val batch = lastFlush.now().transformWith { _ =>
          handleStores(stores, underlying).flatMap(_ => handleReads(reads, underlying))
        }
        lastFlush.set(batch)
      } else underlying.flush(forceFlush)
    }
  }

  private def clear(operation: => Future[SyncSuccess]): Future[SyncSuccess] = synchronized {
    flush(true)
    val clearing = lastFlush.now().transformWith(_ => attempt(operation))
    lastFlush.set(clearing.map(_ => ()))
    clearing
  }
  override def clearValue(context: SyncContext)(implicit ec: ExecutionContext): Future[SyncSuccess] = clear(underlying.clearValue(context))
  override def clearValues(context: UsageContext)(implicit ec: ExecutionContext): Future[SyncSuccess] = clear(underlying.clearValues(context))
}

object BufferedSyncDestinationHandler {
  private given ExecutionContext = ExecutionContext.global
  private def attempt[A](run: => Future[A]): Future[A] = Try(run).fold(Future.failed, identity)

  // A delegate may buffer too. Register first, then flush; flushing before registration can leave callers waiting forever.
  private def execute[A](underlying: SyncDestinationHistoryHandler)(operation: => Future[A]): Future[A] = attempt {
    val pending = operation
    underlying.flush(true)
    pending
  }

  private def handleReads(reads: Seq[CachedReadRequest], underlying: SyncDestinationHistoryHandler): Future[Unit] =
    Future.traverse(reads.groupBy(request => request.usageContext -> request.range).toSeq) { case ((context, range), requests) =>
      val reading = range match {
        case None => execute(underlying)(underlying.readHistoriesJson(context))
        case Some(window) =>
          val keys = requests.collect { case request: CachedRangeRead => request.keys }.flatten.toSeq.distinct
          execute(underlying)(underlying.readHistoriesInRange(context, keys, window.starting, window.until, window.strategy))
            .map(values => FetchResponse.fromMap(LocalDateTime.now(), values.map((key, value) => key -> value.serialized)))
      }
      reading.transform { outcome =>
        requests.foreach(_.fulfillWith(outcome))
        Success(())
      }
    }.map(_ => ())

  private def handleStores(stores: Seq[CachedStoreRequest], underlying: SyncDestinationHistoryHandler): Future[Unit] = {
    if stores.isEmpty then Future.unit else {
      // Merge queued deltas per key so that idempotent events are not lost when multiple
      // stores for the same key arrive before the buffer flushes.
      val storing = attempt {
        val merged = stores.foldLeft(Map.empty[SyncContext, Vector[String]]) { (acc, request) =>
          acc ++ request.toStore.map { (key, json) =>
            key -> (acc.getOrElse(key, Vector.empty) :+ json)
          }
        }.view.map { (key, versions) =>
          key -> InteractionHistoryJson.mergeSerialized(versions)
        }.toMap
        execute(underlying)(underlying.storeHistoriesJson(merged))
      }
      storing.transform { outcome =>
        stores.foreach(_.promise.tryComplete(outcome))
        Success(())
      }
    }
  }

  private case class Range(starting: LocalDateTime, until: LocalDateTime, strategy: EventSyncStrategy)
  private sealed trait CachedRequest { def requestTimestamp: LocalDateTime }
  private case class CachedStoreRequest(requestTimestamp: LocalDateTime, toStore: Map[SyncContext, String], promise: Promise[SyncSuccess])
  private sealed trait CachedReadRequest extends CachedRequest {
    def fulfillWith(data: Try[FetchResponse[SyncContext, String]]): Unit
    def usageContext: UsageContext
    def range: Option[Range]
  }
  private case class CachedReadRequestMultipleRaw(requestTimestamp: LocalDateTime, usageContext: UsageContext, promise: Promise[FetchResponse[SyncContext, String]]) extends CachedReadRequest {
    val range: Option[Range] = None
    override def fulfillWith(data: Try[FetchResponse[SyncContext, String]]): Unit = promise.tryComplete(data)
  }
  private case class CachedRangeRead(requestTimestamp: LocalDateTime, usageContext: UsageContext,
      keys: Set[SyncContext], window: Range, promise: Promise[FetchResponse[SyncContext, String]]) extends CachedReadRequest {
    val range: Option[Range] = Some(window)
    def fulfillWith(data: Try[FetchResponse[SyncContext, String]]): Unit = promise.tryComplete(data)
  }

}
