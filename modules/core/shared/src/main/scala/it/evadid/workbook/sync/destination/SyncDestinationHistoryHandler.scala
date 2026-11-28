package it.evadid.workbook.sync.destination


import it.evadid.distribution.command.SerializedException
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.sync.model.{FetchResponse, InteractionHistoryJson}
import it.evadid.workbook.sync.{EventSyncStrategy, SyncContext, SyncSuccess, UsageContext}
import it.evadid.workbook.history.InteractionHistory
import it.evadid.workbook.history.events.EventId
import upickle.default.*

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

trait SyncDestinationHistoryHandler {

  def syncLogger: SyncLogger

  def definition: SyncDestinationDefinition

  def clearValue(context: SyncContext)(implicit ec: ExecutionContext): Future[SyncSuccess[SyncContext]]

  def clearValues(context: UsageContext)(implicit ec: ExecutionContext): Future[SyncSuccess[SyncContext]]

  /** Full user reset across workbooks; local answer stores are cleared completely. */
  def clearAllHistories(context: UsageContext)(using ExecutionContext): Future[SyncSuccess[SyncContext]]

  /** The result names the sent events that are stored once the write has committed. */
  def storeHistory(syncContext: SyncContext, history: InteractionHistoryJson)(using ExecutionContext): Future[SyncSuccess[EventId]]

  def storeHistories(histories: Map[SyncContext, InteractionHistoryJson])(using ExecutionContext): Future[SyncSuccess[EventId]] =
    Future.traverse(histories.toList)(storeHistory).map(_.foldLeft(SyncSuccess.emptyNow[EventId]())(_.combine(_)))

  def flush(forceFlush: Boolean): Unit = {}

  def readHistory(syncContext: SyncContext)(using ExecutionContext): Future[FetchResponse[SyncContext, InteractionHistoryJson]] =
    readHistories(syncContext.toUsageContext).map(response => response.copy(fetchedValues = response.fetchedValues.filter(_.dataKey == syncContext)))

  /** The readable histories of `usageContext`; unreadable records are discarded where they are decoded. */
  def readHistories(usageContext: UsageContext)(using ExecutionContext): Future[FetchResponse[SyncContext, InteractionHistoryJson]]

  def readHistoriesInRange(usageContext: UsageContext, keys: Seq[SyncContext], starting: Instant,
      until: Instant, strategy: EventSyncStrategy)(using ec: ExecutionContext): Future[Map[SyncContext, InteractionHistoryJson]] = {
    require(!until.isBefore(starting), "History range end must not precede its start")
    val selected = keys.toSet
    readHistories(usageContext).map(response => InteractionHistoryJson.readable(
      response.fetchedValues.filter(entry => selected(entry.dataKey)).map(entry => entry.dataKey -> entry.dataValue))(
      identity, strategy, starting, until,
      (key, error) => syncLogger.logExceptionWarn(s"Discarded the unreadable history of ${key.keyForSerialisation}", error)))
  }

  def readTypedHistory[F <: InteractionFormat](syncContext: SyncContext)(using ec: ExecutionContext, format: ValueOf[F]): Future[InteractionHistory[F]] =
    readHistory(syncContext).flatMap(response => SyncDestinationHistoryHandler.readHistoryFrom[F](syncContext, response).fold(Future.failed, Future.successful))

  lazy val toBuffered: BufferedSyncDestinationHandler = {
    BufferedSyncDestinationHandler(this)
  }

}


object SyncDestinationHistoryHandler {

  private[sync] def readHistoryFrom[F <: InteractionFormat](syncContext: SyncContext,
      response: FetchResponse[SyncContext, InteractionHistoryJson])(using format: ValueOf[F]): Either[SerializedException, InteractionHistory[F]] =
    response.fetchedValues.find(_.dataKey == syncContext).toRight(SerializedException(s"No history for $syncContext"))
      .flatMap(entry => scala.util.Try(entry.dataValue.typedAs(format.value)).toEither.left.map(SerializedException(_)))

  /** A stored record, or None (logged) when it cannot be read. */
  private def decoded(json: String, logger: SyncLogger): Option[InteractionHistoryJson] =
    scala.util.Try(InteractionHistoryJson.deserialize(json)).fold(error => {
      logger.logExceptionWarn("Discarding malformed interaction history", error)
      None
    }, Some(_))

  def fromBasic(basic: SyncDestinationHandlerBasic): SyncDestinationHistoryHandler = {
    val typed = SyncDestinationHandlerForType.fromBasic(basic, SyncContext.serializer, it.evadid.core.util.io.Serializer.rawStringIO)

    new SyncDestinationHistoryHandler {
      override def syncLogger: SyncLogger = basic.syncLogger

      override def definition: SyncDestinationDefinition = basic.underlyingDefinition

      override def clearAllHistories(context: UsageContext)(using ExecutionContext): Future[SyncSuccess[SyncContext]] =
        basic.clearAll().map(_.collect(Function.unlift(key => SyncContext.serializer.tryDeserialize(key).toOption)))

      override def clearValue(context: SyncContext)(implicit ec: ExecutionContext): Future[SyncSuccess[SyncContext]] = typed.clearValue(context)

      override def clearValues(context: UsageContext)(implicit ec: ExecutionContext): Future[SyncSuccess[SyncContext]] = readHistories(context).flatMap(response =>
        Future.traverse(response.fetchedValues.map(_.dataKey))(clearValue).map(_.foldLeft(SyncSuccess.emptyNow[SyncContext]())(_.combine(_))))

      override def storeHistory(context: SyncContext, incoming: InteractionHistoryJson)(using ExecutionContext): Future[SyncSuccess[EventId]] =
        basic.updateElement(SyncContext.serializer.serialize(context), previous => {
          // The exercise changed its type: its earlier answers belong to another exercise and are replaced.
          val saved = previous.flatMap(decoded(_, syncLogger)).filter(_.format == incoming.format)
          val merged = saved.fold(incoming)(_.merge(incoming))
          merged.serialized -> SyncSuccess.immutable(incoming.eventIds, saved.fold(Set.empty)(_.eventIds), merged.eventIds)
        })

      override def readHistories(usageContext: UsageContext)(using ExecutionContext): Future[FetchResponse[SyncContext, InteractionHistoryJson]] =
        typed.readFullStore.map(stored => FetchResponse.fromMap(Instant.now(), stored.collect {
          case (context, json) if context.toUsageContext == usageContext => decoded(json, syncLogger).map(context -> _)
        }.flatten.toMap))
    }
  }

}
