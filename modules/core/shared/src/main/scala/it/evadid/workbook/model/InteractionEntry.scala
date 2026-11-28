package it.evadid.workbook.model


import it.evadid.core.datastructures.storage.remote.RemoteHistoryCache.{CacheState, RemoteHistoryValue}
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.sync.SyncConnection
import it.evadid.workbook.sync.{FormattedSyncDestination, SyncContext, SyncInformationWithContext, UpdateImportance}
import it.evadid.workbook.history.{InteractionVariableHistory, InteractionVariableState}
import it.evadid.workbook.history.events.{AnswerChangedEvent, InteractionEvent}
import it.evadid.workbook.reports.InteractionVariableSyncReport

import java.time.Instant
import scala.util.Try

/** The history of one interaction in an [[InteractionInfo]] and what each destination is known to hold of it. */
final case class InteractionEntry[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], syncContext: SyncContext,
    cache: CacheState[SyncContext, F, FormattedSyncDestination[F]]) {

  def history: InteractionVariableHistory[F] = cache.histories(syncContext)

  /** Every history holds a current answer: the default one until another is recorded. */
  def currentState: InteractionVariableState[InteractionFormat.State[F]] = history.historyStates.lastStateOption.get

  def currentValue: InteractionFormat.State[F] = currentState.value

  /** Applies `transition` unless it adds an answer the exercise rejects; that failure is returned instead. */
  def withHistory(transition: InteractionVariableHistory[F] => InteractionVariableHistory[F]): Try[InteractionEntry[F]] = Try {
    val next = transition(history)
    InteractionEntry.validate(interaction, next.events.values -- history.events.values).get
    val withAnswer = if next.historyStates.lastStateOption.isEmpty then next.mergeWith(InteractionEntry.defaultHistory(interaction)) else next
    copy(cache = cache.copy(histories = cache.histories.updated(syncContext, withAnswer.cleanedHistory())))
  }

  def destinations(connection: SyncConnection): Set[FormattedSyncDestination[F]] =
    connection.destinations.map(information => FormattedSyncDestination(information, interaction.interactionFormat))

  /** Keeps what kept destinations hold and forgets the others; new destinations start unknown. */
  def connectedTo(connection: SyncConnection): InteractionEntry[F] = copy(cache = cache.trackingOnly(destinations(connection)))

  /** All destinations of `connection` and those known to hold the latest answer with all its feedback events. */
  def report(connection: SyncConnection): InteractionVariableSyncReport[SyncInformationWithContext] =
    InteractionVariableSyncReport(connection.destinations,
      cache.confirmedDestinations(syncContext).intersect(destinations(connection)).map(_.information))
}

object InteractionEntry {
  def create[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], syncContext: SyncContext,
      connection: SyncConnection): InteractionEntry[F] =
    InteractionEntry(interaction, syncContext,
      CacheState(Map(syncContext -> defaultHistory(interaction)), Map.empty[FormattedSyncDestination[F], Map[SyncContext, RemoteHistoryValue]]))
      .connectedTo(connection)

  def defaultHistory[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): InteractionVariableHistory[F] =
    InteractionVariableHistory.empty(interaction.interactionFormat).addState(
      InteractionVariableState[InteractionFormat.State[F]](interaction.defaultValue, UpdateImportance.DEFAULT, Instant.now()))

  /** Exercise-specific constraints of the answers among `events`; feedback events only refer to answers. */
  def validate[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], events: Iterable[InteractionEvent[F]]): Try[Unit] =
    Try(events.foreach { case answer: AnswerChangedEvent[F] => interaction.validateState(answer.newState); case _ => () })
}
