package it.evadid.workbook.interaction.sync

import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.destination.{SyncDestinationDefinition, SyncDestinationHistoryHandler}
import it.evadid.workbook.interaction.sync.model.SyncDirection.CLIENT_TO_SERVER
import it.evadid.workbook.interaction.sync.model.{DataEntryToSync, FetchResponse}
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory}
import upickle.default.{ReadWriter, write}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

/** Sync destination with event-based history. The handler stores and fetches InteractionVariableHistory directly. */
case class SyncInformationWithContext(syncInformation: SyncInformation, usageContext: UsageContext, resolveDefinition: SyncDestinationDefinition => SyncDestinationHistoryHandler) {

  private given ec: ExecutionContext = ExecutionContext.global

  val handler: SyncDestinationHistoryHandler = resolveDefinition(syncInformation.syncDestination)

  def serialize[T: ReadWriter, GA: ReadWriter, SA: ReadWriter](interaction: InteractionVariable[T, GA, SA]): InteractionVariableHistory[T, GA, SA] = {
    interaction.currentHistory.cleanWithStrategy(syncInformation.syncStrategy)
  }

  /** Read the history for a specific context. */
  def readHistory[T: ReadWriter, GA: ReadWriter, SA: ReadWriter](context: SyncContext)(implicit ec: ExecutionContext): Future[InteractionVariableHistory[T, GA, SA]] = {
    handler.readHistoryJson(context).flatMap { response =>
      SyncDestinationHistoryHandler.readHistoryFrom(
        it.evadid.core.util.io.Serializer.fromUpickleJson(InteractionVariableHistory.derived$ReadWriter[T, GA, SA]),
        context,
        response
      ).toEither match {
        case Right(history) => Future.successful(history)
        case Left(error) => Future.failed(new RuntimeException(error.getMessage, error.throwable.getOrElse(null)))
      }
    }
  }

  /** Read all histories for the usage context. */
  def readHistories[T: ReadWriter, GA: ReadWriter, SA: ReadWriter](variables: Seq[InteractionVariable[T, GA, SA]])(implicit ec: ExecutionContext): Future[Map[SyncContext, InteractionVariableHistory[T, GA, SA]]] = {
    handler.readHistoriesJson(usageContext).flatMap { response =>
      val serializer = it.evadid.core.util.io.Serializer.fromUpickleJson(InteractionVariableHistory.derived$ReadWriter[T, GA, SA])
      Future.successful(SyncDestinationHistoryHandler.readHistoriesFrom(variables, usageContext, response))
    }
  }

  /** Write the history for a specific context. The history is serialized using the derived ReadWriter. */
  def writeHistory[T: ReadWriter, GA: ReadWriter, SA: ReadWriter](context: SyncContext, history: InteractionVariableHistory[T, GA, SA])(implicit ec: ExecutionContext): Future[SyncSuccess] = {
    val serialized = write(history)
    handler.storeHistoryJson(context, serialized)
  }

  /** Write multiple histories. Histories are serialized using the derived ReadWriter. */
  def writeHistories[T: ReadWriter, GA: ReadWriter, SA: ReadWriter](histories: Map[SyncContext, InteractionVariableHistory[T, GA, SA]])(implicit ec: ExecutionContext): Future[SyncSuccess] = {
    val serialized = histories.map { case (ctx, history) => ctx -> write(history) }
    handler.storeHistoriesJson(serialized)
  }

  def flush(forceFlush: Boolean): Unit = handler.flush(forceFlush)
}
