package it.evadid.workbook.interaction.state

import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.interaction.sync.SyncConnection
import it.evadid.workbook.interaction.sync.{SyncInformationWithContext, UpdateImportance, UsageContext}
import it.evadid.workbook.interaction.variable.InteractionVariableState.{DesignatedInteractionState, InteractionVariableStateChanged}
import it.evadid.workbook.interaction.variable.{InteractionScaffoldingAndGradingControl, InteractionVariableHistory, InteractionVariableState}
import it.evadid.workbook.meta.InteractionVariableSyncReport

import java.time.LocalDateTime
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
  def historyOf[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): InteractionVariableHistory[F] =
    entryOption(interaction).fold(InteractionEntry.defaultHistory(interaction))(_.history)

  /** Changes the history of `interaction`; the result fails if it is not part of this state or the exercise rejects
    * an answer of the new history.
    */
  def withHistory[F <: InteractionFormat](interaction: WorkbookInteractionElement[F])(
      transition: InteractionVariableHistory[F] => InteractionVariableHistory[F]): Try[InteractionInfo] =
    Try(entryOption(interaction).getOrElse(throw new IllegalArgumentException(
      s"${interaction.keyForSerialization} is not part of the answers of $usageContext")))
      .flatMap(_.withHistory(transition)).map(withEntry)

  /** Records a user's answer whose importance `relevance` decides from the change; an unchanged answer is kept. */
  def withUserChange[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], value: InteractionFormat.State[F],
      relevance: InteractionVariableStateChanged[InteractionFormat.State[F]] => UpdateImportance,
      timestamp: LocalDateTime = LocalDateTime.now()): Try[InteractionInfo] =
    Try(interaction.validateState(value)).flatMap(_ => withHistory(interaction)(history => {
      val last = history.historyStates.lastStateOption.get
      if last.value == value then history
      else history.addState(InteractionVariableState[InteractionFormat.State[F]](value,
        relevance(InteractionVariableStateChanged(last, DesignatedInteractionState(value, timestamp))), timestamp))
    }))

  /** Records `value` with `importance`; repeating the current answer and importance changes nothing. */
  def withAnswer[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], value: InteractionFormat.State[F],
      importance: UpdateImportance, timestamp: LocalDateTime = LocalDateTime.now()): Try[InteractionInfo] =
    Try(interaction.validateState(value)).flatMap(_ => withHistory(interaction)(history =>
      if history.historyStates.lastStateOption.exists(last => last.value == value && last.updateImportance == importance) then history
      else history.addState(InteractionVariableState[InteractionFormat.State[F]](value, importance, timestamp))))

  /** All destinations and those known to hold the latest answer of `interaction` with all its feedback events. */
  def reportOf(interaction: WorkbookInteractionElement[? <: InteractionFormat]): InteractionVariableSyncReport[SyncInformationWithContext] =
    entryOption(interaction).fold(InteractionVariableSyncReport(Set.empty[SyncInformationWithContext], Set.empty))(_.report(connection))

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

  /** Creates the feedback computations of each interaction for a new session. */
  trait FeedbackFactory {
    def feedbackFor[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], session: Session): InteractionScaffoldingAndGradingControl[F]
  }

  /** Default answers for `interactions`; interactions sharing a serialization key keep the first. */
  def create(usageContext: UsageContext, interactions: Seq[WorkbookInteractionElement[? <: InteractionFormat]], connection: SyncConnection,
      feedback: FeedbackFactory): InteractionInfo = {
    val session = Session(UUID.randomUUID().toString)
    def entryFor[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): InteractionEntry[F] =
      InteractionEntry.create(interaction, usageContext.toSyncContext(interaction.keyForSerialization), connection,
        feedback.feedbackFor(interaction, session))
    InteractionInfo(session, usageContext, connection,
      interactions.reverse.map(interaction => interaction.keyForSerialization -> entryFor(interaction)).toMap)
  }
}
