package it.evadid.workbook.interaction.sync.destination

import it.evadid.distribution.command.SerializedException
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.interaction.sync.model.{FetchResponse, InteractionHistoryJson}
import it.evadid.workbook.interaction.sync.{EventSyncStrategy, SyncContext, SyncSuccess, UsageContext}
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory}
import upickle.default.*

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait SyncDestinationHistoryHandler {

  def syncLogger: SyncLogger

  def definition: SyncDestinationDefinition

  def clearValue(context: SyncContext)(implicit ec: ExecutionContext): Future[SyncSuccess]

  def clearValues(context: UsageContext)(implicit ec: ExecutionContext): Future[SyncSuccess]

  /** Full user reset across workbooks; local answer stores are cleared completely. */
  def clearAllHistories(context: UsageContext)(using ExecutionContext): Future[SyncSuccess]

  def storeHistoryJson(syncContext: SyncContext, historyJson: String)(implicit ec: ExecutionContext): Future[SyncSuccess]

  def storeHistoriesJson(contextJsonMap: Map[SyncContext, String])(implicit ec: ExecutionContext): Future[SyncSuccess] = {
    Future.traverse(contextJsonMap.toList)(storeHistoryJson).map(_.fold(SyncSuccess.emptyNow())(_.combine(_)))
  }

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
    readHistoriesJson(usageContext).map(response => response.fetchedValues.filter(entry => selected(entry.dataKey)).map { entry =>
      entry.dataKey -> InteractionHistoryJson.deserialize(entry.dataValue).cleanWithStrategy(strategy).between(starting, until)
    }.toMap)
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

      override def clearAllHistories(context: UsageContext)(using ExecutionContext): Future[SyncSuccess] = basic.clearAll()

      override def clearValue(context: SyncContext)(implicit ec: ExecutionContext): Future[SyncSuccess] = typed.clearValue(context)

      override def clearValues(context: UsageContext)(implicit ec: ExecutionContext): Future[SyncSuccess] = readHistoriesJson(context).flatMap(response => {
        val containedKeys = response.fetchedValues.map(_.dataKey).filter(cur => cur.programId == context.programId && cur.scenarioId == context.scenarioId && cur.userId == context.userId)
        Future.traverse(containedKeys)(clearValue).map(_ => SyncSuccess(0, 0, containedKeys.size, LocalDateTime.now()))
      })

      override def storeHistoryJson(context: SyncContext, json: String)(using ec: ExecutionContext): Future[SyncSuccess] = {
        scala.util.Try(InteractionHistoryJson.deserialize(json)).fold(Future.failed, incoming =>
          basic.updateElement(SyncContext.serializer.serialize(context), previous => {
            val saved = previous.flatMap(value => scala.util.Try(InteractionHistoryJson.deserialize(value)).fold(error => {
              syncLogger.logExceptionWarn("Discarding malformed stored interaction history", error)
              None
            }, Some(_)))
            saved.fold(incoming)(_.merge(incoming)).serialized
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
