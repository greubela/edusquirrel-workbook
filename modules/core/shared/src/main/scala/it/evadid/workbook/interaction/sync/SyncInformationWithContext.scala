package it.evadid.workbook.interaction.sync

import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.destination.{SyncDestinationDefinition, SyncDestinationHistoryHandler}
import it.evadid.workbook.interaction.sync.model.SyncDirection.CLIENT_TO_SERVER
import it.evadid.workbook.interaction.sync.model.{DataEntryToSync, FetchResponse}
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

/** Sync destination with event-based history. The handler stores and fetches InteractionVariableHistory directly. */
case class SyncInformationWithContext(syncInformation: SyncInformation, usageContext: UsageContext, resolveDefinition: SyncDestinationDefinition => SyncDestinationHistoryHandler) {

  private given ec: ExecutionContext = ExecutionContext.global

  val handler: SyncDestinationHistoryHandler = resolveDefinition(syncInformation.syncDestination)

  def serialize[T, GA, SA](interaction: InteractionVariable[T, GA, SA]): InteractionVariableHistory[T, GA, SA] = {
    interaction.currentHistory.cleanWithStrategy(syncInformation.syncStrategy)
  }

  /** Read the history for a specific context. */
  def readHistory[T: ReadWriter, GA: ReadWriter, SA: ReadWriter](context: SyncContext)(implicit ec: ExecutionContext): Future[InteractionVariableHistory[T, GA, SA]] = {
    handler.readHistory[T, GA, SA](context)
  }

  /** Read all histories for the usage context. */
  def readHistories[T: ReadWriter, GA: ReadWriter, SA: ReadWriter](variables: Seq[InteractionVariable[T, GA, SA]])(implicit ec: ExecutionContext): Future[Map[SyncContext, InteractionVariableHistory[T, GA, SA]]] = {
    handler.readHistories(usageContext, variables)
  }

  /** Write the history for a specific context. */
  def writeHistory[T, GA, SA](context: SyncContext, history: InteractionVariableHistory[T, GA, SA])(implicit ec: ExecutionContext): Future[SyncSuccess] = {
    val serialized = handler.serializerHistory.serialize(history)
    handler.storeHistoryJson(context, serialized)
  }

  /** Write multiple histories. */
  def writeHistories[T, GA, SA](histories: Map[SyncContext, InteractionVariableHistory[T, GA, SA]])(implicit ec: ExecutionContext): Future[SyncSuccess] = {
    val serialized = histories.map { case (ctx, history) => ctx -> handler.serializerHistory.serialize(history) }
    handler.storeHistoriesJson(serialized)
  }

  def flush(forceFlush: Boolean): Unit = handler.flush(forceFlush)
}
