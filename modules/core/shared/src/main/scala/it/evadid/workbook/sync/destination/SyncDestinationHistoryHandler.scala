package it.evadid.workbook.sync.destination

import it.evadid.distribution.command.SerializedException
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.sync.model.{FetchResponse, InteractionHistoryJson}
import it.evadid.workbook.sync.{EventSyncStrategy, SyncContext, SyncSuccess, UsageContext}
import it.evadid.workbook.history.InteractionVariableHistory
import it.evadid.workbook.history.events.EventId
import upickle.default.*

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

trait SyncDestinationHistoryHandler {

  def syncLogger: SyncLogger

  def definition: SyncDestinationDefinition

  def clearValue(context: SyncContext)(implicit ec: ExecutionContext): Future[SyncSuccess[SyncContext]]

  def clearValues(context: UsageContext)(implicit ec: ExecutionContext): Future[SyncSuccess[SyncContext]]

  /** Full user reset across workbooks; local answer stores are cleared completely. */
  def clearAllHistories(context: UsageContext)(using ExecutionContext): Future[SyncSuccess[SyncContext]]

  /** The result names the sent events that are stored once the write has committed. */
  def storeHistoryJson(syncContext: SyncContext, historyJson: String)(implicit ec: ExecutionContext): Future[SyncSuccess[EventId]]

  def storeHistoriesJson(contextJsonMap: Map[SyncContext, String])(implicit ec: ExecutionContext): Future[SyncSuccess[EventId]] =
    Future.traverse(contextJsonMap.toList)(storeHistoryJson).map(_.foldLeft(SyncSuccess.emptyNow[EventId]())(_.combine(_)))

  def flush(forceFlush: Boolean): Unit = {}

  def readHistoryJson(syncContext: SyncContext)(implicit ec: ExecutionContext): Future[FetchResponse[SyncContext, String]] = {
    readHistoriesJson(syncContext.toUsageContext).map(resp => {
      val newSet = resp.fetchedValues.filter(_.dataKey.doesMatch(syncContext.toUsageContext)).filter(_.dataKey.keyForSerialisation == syncContext.keyForSerialisation)
      resp.copy(fetchedValues = newSet)
    })
  }

  def readHistoriesJson(usageContext: UsageContext)(implicit ec: ExecutionContext): Future[FetchResponse[SyncContext, String]]

  def readHistoriesInRange(usageContext: UsageContext, keys: Seq[SyncContext], starting: LocalDateTime,
      until: LocalDateTime, strategy: EventSyncStrategy)(using ec: ExecutionContext): Future[Map[SyncContext, InteractionHistoryJson]] = {
    require(!until.isBefore(starting), "History range end must not precede its start")
    val selected = keys.toSet
    readHistoriesJson(usageContext).map(response => InteractionHistoryJson.readable(
      response.fetchedValues.filter(entry => selected(entry.dataKey)).map(entry => entry.dataKey -> entry.dataValue))(
      InteractionHistoryJson.deserialize, strategy, starting, until,
      (key, error) => syncLogger.logExceptionWarn(s"Discarded the unreadable history of ${key.keyForSerialisation}", error)))
  }

  def readHistory[F <: InteractionFormat](syncContext: SyncContext)(using ec: ExecutionContext, format: ValueOf[F]): Future[InteractionVariableHistory[F]] =
    readHistoryJson(syncContext).flatMap(response => SyncDestinationHistoryHandler.readHistoryFrom[F](syncContext, response).fold(Future.failed, Future.successful))

  lazy val toBuffered: BufferedSyncDestinationHandler = {
    BufferedSyncDestinationHandler(this)
  }

}


object SyncDestinationHistoryHandler {

  private[sync] def readHistoryFrom[F <: InteractionFormat](syncContext: SyncContext,
      response: FetchResponse[SyncContext, String])(using format: ValueOf[F]): Either[SerializedException, InteractionVariableHistory[F]] =
    response.fetchedValues.find(_.dataKey == syncContext).toRight(SerializedException(s"No history for $syncContext"))
      .flatMap(entry => scala.util.Try(InteractionHistoryJson.deserialize(entry.dataValue).typedAs(format.value)).toEither.left.map(SerializedException(_)))

  private def validHistory(json: String, logger: SyncLogger): Boolean =
    scala.util.Try(InteractionHistoryJson.deserialize(json)).fold(error => {
      logger.logExceptionWarn("Discarding malformed interaction history", error)
      false
    }, _ => true)

  def fromBasic(basic: SyncDestinationHandlerBasic): SyncDestinationHistoryHandler = {
    val typed = SyncDestinationHandlerForType.fromBasic(basic, SyncContext.serializer, it.evadid.core.util.io.Serializer.rawStringIO)

    new SyncDestinationHistoryHandler {
      override def syncLogger: SyncLogger = basic.syncLogger

      override def definition: SyncDestinationDefinition = basic.underlyingDefinition

      override def clearAllHistories(context: UsageContext)(using ExecutionContext): Future[SyncSuccess[SyncContext]] =
        basic.clearAll().map(_.collect(Function.unlift(key => SyncContext.serializer.tryDeserialize(key).toOption)))

      override def clearValue(context: SyncContext)(implicit ec: ExecutionContext): Future[SyncSuccess[SyncContext]] = typed.clearValue(context)

      override def clearValues(context: UsageContext)(implicit ec: ExecutionContext): Future[SyncSuccess[SyncContext]] = readHistoriesJson(context).flatMap(response =>
        Future.traverse(response.fetchedValues.map(_.dataKey))(clearValue).map(_.foldLeft(SyncSuccess.emptyNow[SyncContext]())(_.combine(_))))

      override def storeHistoryJson(context: SyncContext, json: String)(using ec: ExecutionContext): Future[SyncSuccess[EventId]] = {
        scala.util.Try(InteractionHistoryJson.deserialize(json)).fold(Future.failed, incoming =>
          basic.updateElement(SyncContext.serializer.serialize(context), previous => {
            val saved = previous.flatMap(value => scala.util.Try(InteractionHistoryJson.deserialize(value)).fold(error => {
              syncLogger.logExceptionWarn("Discarding malformed stored interaction history", error)
              None
            }, Some(_)))
            val merged = saved.fold(incoming)(_.merge(incoming))
            merged.serialized -> SyncSuccess.immutable(incoming.eventIds, saved.fold(Set.empty)(_.eventIds), merged.eventIds)
          }))
      }

      override def readHistoryJson(syncContext: SyncContext)(implicit ec: ExecutionContext): Future[FetchResponse[SyncContext, String]] = {
        typed.readElementTyped(syncContext).map(curHistoryJson => {
          FetchResponse.fromMap(LocalDateTime.now(), Map(syncContext -> curHistoryJson).filter((_, json) => validHistory(json, syncLogger)))
        })
      }

      override def readHistoriesJson(usageContext: UsageContext)(implicit ec: ExecutionContext): Future[FetchResponse[SyncContext, String]] = {
        typed.readFullStore.map(curMap => {
          val data = curMap.filter((context, json) => context.toUsageContext == usageContext && validHistory(json, syncLogger))
          FetchResponse.fromMap(LocalDateTime.now(), data)
        })
      }
    }
  }

}
