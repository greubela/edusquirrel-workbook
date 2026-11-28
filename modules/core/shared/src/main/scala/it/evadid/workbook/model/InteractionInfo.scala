package it.evadid.workbook.model


import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.sync.SyncConnection
import it.evadid.workbook.sync.{SyncInformationWithContext, UpdateImportance, UsageContext}
import it.evadid.workbook.history.InteractionState.{DesignatedInteractionState, InteractionStateChange}
import it.evadid.workbook.history.{InteractionHistory, InteractionState}
import it.evadid.workbook.reports.InteractionSyncReport

import java.time.Instant
import java.util.UUID
import scala.util.Try

/** The answers of one sync context: each interaction's history and synchronization knowledge, the connection to
  * the destinations and the identity of this session.
  *
  * Interactions are exercise definitions; their answers live only here. An interaction is identified by its
  * serialization key and its instance: a definition of another workbook instance under the same key is not part of
  * this state. Transitions are pure; [[InteractionController]] applies them to the store that holds the state.
  */
final case class InteractionInfo(session: InteractionInfo.Session, usageContext: UsageContext, connection: SyncConnection,
    entries: Map[String, InteractionEntry[? <: InteractionFormat]]) {

  def interactions: Seq[WorkbookInteractionElement[? <: InteractionFormat]] = entries.values.map(_.interaction).toSeq

  def entryOption[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): Option[InteractionEntry[F]] =
    // Instance identity guarantees that the entry has the interaction's format.
    entries.get(interaction.keyForSerialization).filter(_.interaction eq interaction).map(_.asInstanceOf[InteractionEntry[F]])

  def contains(interaction: WorkbookInteractionElement[? <: InteractionFormat]): Boolean = entryOption(interaction).nonEmpty

  /** The history of `interaction`; an interaction that is not part of this state has its default history. */
  def historyOf[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): InteractionHistory[F] =
    entryOption(interaction).fold(InteractionEntry.defaultHistory(interaction))(_.history)

  /** The current answer of `interaction`, with its importance and time. */
  def currentStateOf[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): InteractionState[InteractionFormat.State[F]] =
    historyOf(interaction).latestAnswer.get

  def currentValueOf[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): InteractionFormat.State[F] =
    currentStateOf(interaction).value

  /** Whether `scope` names this state's context and the same interaction instances. */
  def holdsAnswersOf(scope: InteractionScope): Boolean = scope.usageContext == usageContext &&
    scope.interactions.size == entries.size && scope.interactions.forall(contains)

  /** Changes the history of `interaction`; the result fails if it is not part of this state or the exercise rejects
    * an answer the transition adds.
    */
  def withHistory[F <: InteractionFormat](interaction: WorkbookInteractionElement[F])(
      transition: InteractionHistory[F] => InteractionHistory[F]): Try[InteractionInfo] =
    Try(entryOption(interaction).getOrElse(throw new IllegalArgumentException(
      s"${interaction.keyForSerialization} is not part of the answers of $usageContext")))
      .flatMap(_.withHistory(transition)).map(withEntry)

  /** Records a user's answer whose importance `relevance` decides from the change; an unchanged answer is kept. */
  def withUserChange[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], value: InteractionFormat.State[F],
      relevance: InteractionStateChange[InteractionFormat.State[F]] => UpdateImportance,
      timestamp: Instant = Instant.now()): Try[InteractionInfo] =
    withHistory(interaction)(history => {
      val last = history.latestAnswer.get
      if last.value == value then history
      else history.addState(InteractionState[InteractionFormat.State[F]](value,
        relevance(InteractionStateChange(last, DesignatedInteractionState(value, timestamp))), timestamp))
    })

  /** Records `value` with `importance`; repeating the current answer and importance changes nothing. */
  def withAnswer[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], value: InteractionFormat.State[F],
      importance: UpdateImportance, timestamp: Instant = Instant.now()): Try[InteractionInfo] =
    withHistory(interaction)(history =>
      if history.latestAnswer.exists(last => last.value == value && last.updateImportance == importance) then history
      else history.addState(InteractionState[InteractionFormat.State[F]](value, importance, timestamp)))

  /** All destinations and those known to hold the latest answer of `interaction` with all its feedback events. */
  def reportOf(interaction: WorkbookInteractionElement[? <: InteractionFormat]): InteractionSyncReport[SyncInformationWithContext] =
    entryOption(interaction).fold(InteractionSyncReport(Set.empty[SyncInformationWithContext], Set.empty))(_.report(connection))

  def withEntry[F <: InteractionFormat](entry: InteractionEntry[F]): InteractionInfo =
    copy(entries = entries.updated(entry.interaction.keyForSerialization, entry))

  /** The same answers with another connection: histories stay, knowledge of removed destinations is forgotten. */
  def connectedTo(next: SyncConnection): InteractionInfo =
    if next == connection then this
    else copy(connection = next, entries = entries.view.mapValues(_.connectedTo(next)).toMap)
}

object InteractionInfo {
  /** Distinguishes two states of the same context, e.g. before and after a full reset. */
  final case class Session(id: String)

  /** A new session with default answers for the interactions of `scope`; interactions sharing a serialization key
    * keep the first.
    */
  def create(scope: InteractionScope): InteractionInfo = {
    def entryFor[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): InteractionEntry[F] =
      InteractionEntry.create(interaction, scope.usageContext.toSyncContext(interaction.keyForSerialization), scope.connection)
    InteractionInfo(Session(UUID.randomUUID().toString), scope.usageContext, scope.connection,
      scope.interactions.reverse.map(interaction => interaction.keyForSerialization -> entryFor(interaction)).toMap)
  }

  /** No answers yet, before a page or test binds its interactions. */
  def empty(usageContext: UsageContext): InteractionInfo = create(InteractionScope(usageContext, Nil, SyncConnection.offline))
}
