package it.evadid.workbook.control

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.sync.{SyncConnection, SyncContext, SyncInformationWithContext, UpdateImportance}
import it.evadid.workbook.sync.model.InteractionHistoryJson
import it.evadid.workbook.history.InteractionState.InteractionStateChange
import it.evadid.workbook.model.{InteractionEntry, InteractionInfo, InteractionScope}
import it.evadid.workbook.history.{InteractionHistory, InteractionState}
import it.evadid.workbook.abstractions.ExecutionContextInformation
import it.evadid.workbook.reports.InteractionSyncReport

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.util.Try

/** Every change of the [[InteractionInfo]] held by `store`, and the feedback computed for it.
  *
  * Writes are pure transitions of the whole state. Reactions run once a change is observed: a changed answer is
  * graded by the graders that run automatically for it, and a changed history is stored by [[sync]] when `schedule`
  * runs the store, so changes made together (an answer and its grading starts) share one write.
  */
class InteractionController(store: State[InteractionInfo], schedule: (() => Unit) => Unit = run => run()) {
  private given ExecutionContext = ExecutionContext.global
  private type AnyInteraction = WorkbookInteractionElement[? <: InteractionFormat]
  private type Control = FeedbackControl[? <: InteractionFormat]

  def info: InteractionInfo = store.now()
  /** Every new state of the answers, for views that follow them. */
  def changes: ObservableValue[InteractionInfo] = store.observable
  def logger: SyncLogger = info.connection.logger

  /** Storing, reading and resetting these answers in their destinations. */
  val sync: InteractionSync = new InteractionSync(store, schedule, bind(_))

  // OBSERVED CHANGES

  private val observed = State(store.now())

  /** Reactions to a change within one session; a new session starts without them. */
  private def afterChange(previous: InteractionInfo, next: InteractionInfo): Unit =
    if previous.session == next.session then next.entries.foreach { (key, entry) =>
      previous.entries.get(key).filter(_ ne entry).foreach { before =>
        if before.history != entry.history then sync.requestStore(key)
        val current = entry.currentState
        if before.currentValue != current.value && entry.interaction.gradingElements.exists(_.shouldRunGrader(false, current.updateImportance)) then
          feedback(entry.interaction).getOrCalculateGrading()
      }
    }

  // READS

  def currentHistory[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): InteractionHistory[F] = info.historyOf(interaction)
  def currentState[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): InteractionState[InteractionFormat.State[F]] =
    info.currentStateOf(interaction)
  def currentValue[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): InteractionFormat.State[F] = info.currentValueOf(interaction)
  def report(interaction: AnyInteraction): InteractionSyncReport[SyncInformationWithContext] = info.reportOf(interaction)

  // TRANSITIONS

  /** Starts a new session with the default answers of `scope`; the previous state is returned. */
  def bind(scope: InteractionScope): InteractionInfo = {
    val previous = info
    store.set(InteractionInfo.create(scope))
    previous
  }

  /** The same answers with another connection. */
  def connect(connection: SyncConnection): Unit = store.update(_.connectedTo(connection))

  /** Follows the page to `scope`: the same context with the same interaction instances keeps its answers and, if
    * `reconnect`, takes the scope's connection; anything else starts a new session with default answers.
    */
  def follow(scope: InteractionScope, reconnect: Boolean): Unit = store.update { current =>
    if !current.holdsAnswersOf(scope) then InteractionInfo.create(scope)
    else if reconnect then current.connectedTo(scope.connection)
    else current
  }

  /** Changes the history of `interaction`; a history with an answer the exercise rejects is reported and not applied. */
  def updateHistory[F <: InteractionFormat](interaction: WorkbookInteractionElement[F])(
      transition: InteractionHistory[F] => InteractionHistory[F]): Unit =
    store.update(current => orUnchanged(current, interaction, current.withHistory(interaction)(transition)))

  /** Applies a transition result, or reports its failure and keeps `current`. */
  protected def orUnchanged(current: InteractionInfo, interaction: AnyInteraction, next: Try[InteractionInfo]): InteractionInfo =
    next.fold(error => {
      logger.logExceptionWarn(s"Ignored a change of ${interaction.keyForSerialization}", error)
      current
    }, identity)

  /** Records a user's answer; an answer the exercise rejects fails at once and changes nothing. */
  def setStateFromUserInteraction[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], value: InteractionFormat.State[F],
      importance: UpdateImportance, timestamp: Instant = Instant.now()): Unit = {
    interaction.validateState(value)
    store.update(current => orUnchanged(current, interaction, current.withAnswer(interaction, value, importance, timestamp)))
  }

  /** Records a user's change of the answer whose importance `relevance` decides; an unchanged answer is ignored. */
  def applyUserChange[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], value: InteractionFormat.State[F],
      relevance: InteractionStateChange[InteractionFormat.State[F]] => UpdateImportance): Unit = {
    interaction.validateState(value)
    store.update(current => orUnchanged(current, interaction, current.withUserChange(interaction, value, relevance)))
  }

  /** A view of the answer for editors that do not use Airstream; writes are user changes of `importance`. */
  def answerState[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], importance: UpdateImportance): State[InteractionFormat.State[F]] =
    State.fromStorage(() => currentValue(interaction), applyUserChange(interaction, _, _ => importance),
      transform => store.update(current => orUnchanged(current, interaction,
        current.withUserChange(interaction, transform(current.currentValueOf(interaction)), _ => importance))),
      store.observable.deriveValue(_.currentValueOf(interaction)))

  /** Commits a TEMPORARY current answer as MAJOR, so strategies that skip TEMPORARY answers send it. */
  def commitCurrentState[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): Unit =
    updateHistory(interaction)(history => history.latestAnswer.filter(_.updateImportance == UpdateImportance.TEMPORARY)
      .fold(history)(last => history.addState(InteractionState(last.value, UpdateImportance.MAJOR, Instant.now()))))

  /** Back to the default answer; the session and its completed feedback computations stay. */
  def resetLocalHistory[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): Unit =
    updateHistory(interaction)(_ => InteractionEntry.defaultHistory(interaction))

  /** Storage boundary for a stored record: it is merged unless it cannot be read as the interaction's format or
    * holds an answer the exercise rejects. Such a record is reported and discarded; the current history stays.
    */
  def restoreHistory[F <: InteractionFormat](logger: Logger, interaction: WorkbookInteractionElement[F], received: InteractionHistoryJson): Unit =
    store.update(restored(logger, _, interaction, received))

  /** Restores the saved histories of this state's workbook, whoever saved them; other workbooks' histories are
    * ignored, even where their interactions use the same keys.
    */
  def restoreHistories(logger: Logger, data: Map[SyncContext, InteractionHistoryJson]): Unit = store.update { current =>
    val own = data.collect { case (key, history) if key.scenarioId == current.usageContext.scenarioId => key.keyForSerialisation -> history }
    current.interactions.foldLeft(current)((state, interaction) =>
      own.get(interaction.keyForSerialization).fold(state)(restored(logger, state, interaction, _)))
  }

  private def restored[F <: InteractionFormat](logger: Logger, state: InteractionInfo, interaction: WorkbookInteractionElement[F],
      received: InteractionHistoryJson): InteractionInfo =
    Try(received.typedAs(interaction.interactionFormat)).flatMap(history => state.withHistory(interaction)(_.mergeWith(history))).fold(error => {
      logger.logExceptionWarn(s"Discarded the stored history of ${interaction.keyForSerialization}", error)
      state
    }, identity)

  // FEEDBACK

  /** Feedback computations of the current session, created on first use; a new session starts without them. */
  private val feedbacks = State(Map.empty[(InteractionInfo.Session, String), Control])

  /** The feedback of `interaction` in the current session. Requests for an interaction outside it fail. */
  def feedback[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): FeedbackControl[F] = feedbacks.synchronized {
    val session = info.session
    val key = (session, interaction.keyForSerialization)
    // Instance identity guarantees that the control has the interaction's format.
    feedbacks.now().get(key).filter(_.interaction eq interaction).map(_.asInstanceOf[FeedbackControl[F]]).getOrElse {
      val created = new FeedbackControl(feedbackTarget(interaction, session))
      feedbacks.update(all => all.filter((known, _) => known._1 == session).updated(key, created))
      created
    }
  }

  private def feedbackTarget[F <: InteractionFormat](element: WorkbookInteractionElement[F], session: InteractionInfo.Session): FeedbackTarget[F] =
    new FeedbackTarget[F] {
      val interaction: WorkbookInteractionElement[F] = element
      def isCurrent: Boolean = info.session == session && info.contains(interaction)
      def currentHistory: InteractionHistory[F] =
        if isCurrent then info.historyOf(interaction) else InteractionEntry.defaultHistory(interaction)
      def execution: ExecutionContextInformation = info.connection.execution
      def publish(transition: InteractionHistory[F] => InteractionHistory[F]): Unit =
        store.update(current => if current.session != session then current else orUnchanged(current, interaction, current.withHistory(interaction)(transition)))
    }

  // Registered last: every member the reactions use is initialized.
  store.observable.addObserver { next =>
    val previous = observed.now()
    observed.set(next)
    afterChange(previous, next)
  }
}

object InteractionController {
  /** A controller over `store` that stores each change at once. */
  def apply(store: State[InteractionInfo]): InteractionController = new InteractionController(store)
}
