package it.evadid.workbook.interaction.state

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.storage.remote.RemoteHistoryCache
import it.evadid.core.datastructures.storage.remote.RemoteHistoryCache.CacheState
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.interaction.sync.{FormattedSyncDestination, SyncConnection, SyncContext, SyncInformationWithContext, SyncPhases, UpdateImportance, UsageContext}
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import it.evadid.workbook.interaction.variable.InteractionVariableState.InteractionVariableStateChanged
import it.evadid.workbook.interaction.variable.{FeedbackTarget, InteractionScaffoldingAndGradingControl, InteractionVariableHistory, InteractionVariableState}
import it.evadid.workbook.meta.{ExecutionContextInformation, InteractionVariableSyncReport}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/** Every change of the [[InteractionInfo]] held by `store`. Writes are pure transitions of the whole state, so
  * they compose even where the store applies them later (an Airstream transaction). Reactions run once a change is
  * observed: a changed answer is graded by the graders that run automatically, and a changed history is stored
  * through [[requestStore]], which a front end may defer so that changes made together share one write.
  */
class InteractionController(store: State[InteractionInfo]) extends InteractionInfo.FeedbackFactory {
  private given ExecutionContext = ExecutionContext.global
  private type AnyInteraction = WorkbookInteractionElement[? <: InteractionFormat]

  def info: InteractionInfo = store.now()
  def logger: it.evadid.util.logging.derived.SyncLogger = info.connection.logger

  // OBSERVED CHANGES

  private val observed = State(store.now())
  store.observable.addObserver { next =>
    val previous = observed.now()
    observed.set(next)
    afterChange(previous, next)
  }

  /** Reactions to a change within one session; a replaced session starts without them. */
  private def afterChange(previous: InteractionInfo, next: InteractionInfo): Unit =
    if previous.session == next.session then next.entries.foreach { (key, entry) =>
      previous.entries.get(key).filter(_ ne entry).foreach { before =>
        if before.history != entry.history then requestStore(key)
        if before.currentValue != entry.currentValue then entry.feedback.getOrCalculateGrading()
      }
    }

  /** Stores the changed history under `key` of the current session; stores at once by default. */
  protected def requestStore(key: String): Unit = storeEntry(key)

  // READS

  def currentHistory[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): InteractionVariableHistory[F] = info.historyOf(interaction)
  def currentState[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): InteractionVariableState[InteractionFormat.State[F]] =
    currentHistory(interaction).historyStates.lastStateOption.get
  def currentValue[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): InteractionFormat.State[F] = currentState(interaction).value

  /** The feedback of `interaction` in the current session; an interaction outside it gets a control that never publishes. */
  def feedback[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): InteractionScaffoldingAndGradingControl[F] =
    info.entryOption(interaction).fold(feedbackFor(interaction, InteractionInfo.Session("[not bound]")))(_.feedback)

  def report(interaction: AnyInteraction): InteractionVariableSyncReport[SyncInformationWithContext] = info.reportOf(interaction)

  // TRANSITIONS

  /** Replaces the state with default answers for `interactions` in `usageContext`; the previous state is returned. */
  def bind(usageContext: UsageContext, interactions: Seq[AnyInteraction], connection: SyncConnection): InteractionInfo = {
    val previous = info
    store.set(InteractionInfo.create(usageContext, interactions, connection, this))
    previous
  }

  /** The same answers with another connection. */
  def connect(connection: SyncConnection): Unit = store.update(_.connectedTo(connection))

  /** Changes the history of `interaction`; a history with an answer the exercise rejects is reported and not applied. */
  def updateHistory[F <: InteractionFormat](interaction: WorkbookInteractionElement[F])(
      transition: InteractionVariableHistory[F] => InteractionVariableHistory[F]): Unit =
    store.update(current => orUnchanged(current, interaction, current.withHistory(interaction)(transition)))

  /** Applies a transition result, or reports its failure and keeps `current`. */
  protected def orUnchanged(current: InteractionInfo, interaction: AnyInteraction, next: Try[InteractionInfo]): InteractionInfo =
    next.fold(error => {
      logger.logExceptionWarn(s"Ignored a change of ${interaction.keyForSerialization}", error)
      current
    }, identity)

  /** Records a user's answer; an answer the exercise rejects fails at once and changes nothing. */
  def setStateFromUserInteraction[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], value: InteractionFormat.State[F],
      importance: UpdateImportance, timestamp: LocalDateTime = LocalDateTime.now()): Unit = {
    interaction.validateState(value)
    store.update(current => orUnchanged(current, interaction, current.withAnswer(interaction, value, importance, timestamp)))
  }

  /** Records a user's change of the answer whose importance `relevance` decides; an unchanged answer is ignored. */
  def applyUserChange[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], value: InteractionFormat.State[F],
      relevance: InteractionVariableStateChanged[InteractionFormat.State[F]] => UpdateImportance): Unit = {
    interaction.validateState(value)
    store.update(current => orUnchanged(current, interaction, current.withUserChange(interaction, value, relevance)))
  }

  /** A view of the answer for editors that do not use Airstream; writes are user changes of `importance`. */
  def answerState[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], importance: UpdateImportance): State[InteractionFormat.State[F]] =
    State.fromStorage(() => currentValue(interaction), applyUserChange(interaction, _, _ => importance),
      transform => applyUserChange(interaction, transform(currentValue(interaction)), _ => importance),
      store.observable.deriveValue(_.historyOf(interaction).historyStates.lastStateOption.get.value))

  /** Commits a TEMPORARY current answer as MAJOR, so strategies that skip TEMPORARY answers send it. */
  def commitCurrentState[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): Unit =
    updateHistory(interaction)(history => history.historyStates.lastStateOption.filter(_.updateImportance == UpdateImportance.TEMPORARY)
      .fold(history)(last => history.addState(InteractionVariableState(last.value, UpdateImportance.MAJOR, LocalDateTime.now()))))

  /** Back to the default answer; the session and its completed feedback computations stay. */
  def resetLocalHistory[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): Unit =
    updateHistory(interaction)(_ => InteractionEntry.defaultHistory(interaction))

  /** Storage boundary for a stored record: it is merged unless it cannot be read as the interaction's format or
    * holds an answer the exercise rejects. Such a record is reported and discarded; the current history stays.
    */
  def restoreHistory[F <: InteractionFormat](logger: Logger, interaction: WorkbookInteractionElement[F], received: InteractionHistoryJson): Unit =
    Try(received.typedAs(interaction.interactionFormat)).flatMap(history => InteractionEntry.validate(interaction, history).map(_ => history))
      .fold(error => logger.logExceptionWarn(s"Discarded the stored history of ${interaction.keyForSerialization}", error),
        history => updateHistory(interaction)(_.mergeWith(history)))

  /** Restores the saved histories of this state's workbook, whoever saved them; other workbooks' histories are
    * ignored, even where their interactions use the same keys.
    */
  def restoreHistories(logger: Logger, data: Map[SyncContext, InteractionHistoryJson]): Unit = {
    val current = info
    val own = data.collect { case (key, history) if key.scenarioId == current.usageContext.scenarioId => key.keyForSerialisation -> history }
    current.interactions.foreach(interaction => own.get(interaction.keyForSerialization).foreach(restoreHistory(logger, interaction, _)))
  }

  // SYNCHRONIZATION

  /** Writes the events each destination is missing; buffered destinations send them when flushed. */
  def store(interaction: AnyInteraction, forceFlush: Boolean = false): Future[Unit] = storeEntry(interaction.keyForSerialization, forceFlush)

  /** Reads new events from every destination, fully once the last full read is five minutes old. A read failure is
    * reported once the other destinations have been read.
    */
  def fetch(interaction: AnyInteraction, forceFlush: Boolean = false): Future[Unit] = fetchEntry(interaction.keyForSerialization, forceFlush)

  /** Stores the entry under `key` of `state` (by default the current one); a retired state's entry is sent with
    * its own connection, and its results reach only that state.
    */
  def storeEntry(key: String, forceFlush: Boolean = false, state: InteractionInfo = info): Future[Unit] = withEntry(key, state)(new EntryOperation {
    def apply[F <: InteractionFormat](cache: RemoteHistoryCache[SyncContext, F, FormattedSyncDestination[F]], entry: InteractionEntry[F]) =
      cache.storeAll(forceFlush)
  })

  def fetchEntry(key: String, forceFlush: Boolean = false, state: InteractionInfo = info): Future[Unit] = withEntry(key, state)(new EntryOperation {
    def apply[F <: InteractionFormat](cache: RemoteHistoryCache[SyncContext, F, FormattedSyncDestination[F]], entry: InteractionEntry[F]) =
      SyncPhases.settled(entry.destinations(state.connection).toSeq.map(destination => SyncPhases.attempt(
        cache.refreshFrom(destination, Set(entry.syncContext), LocalDateTime.now().minusMinutes(5), forceFlush)).map(_ => ())))
  })

  /** Sends the buffered requests of every destination of the current connection. */
  def flush(): Unit = info.connection.flush()

  private trait EntryOperation {
    def apply[F <: InteractionFormat](cache: RemoteHistoryCache[SyncContext, F, FormattedSyncDestination[F]], entry: InteractionEntry[F]): Future[Unit]
  }

  /** Runs `operation` on the cache of the entry under `key` in `state`; its results reach only that session, and
    * knowledge of destinations the session no longer uses is ignored.
    */
  private def withEntry(key: String, state: InteractionInfo)(operation: EntryOperation): Future[Unit] = SyncPhases.attempt {
    state.entries.get(key).fold(Future.unit)(entry => run(state, entry, operation))
  }

  private def run[F <: InteractionFormat](started: InteractionInfo, entry: InteractionEntry[F], operation: EntryOperation): Future[Unit] = {
    val interaction = entry.interaction
    def inSession(current: InteractionInfo): Option[InteractionEntry[F]] =
      Option.when(current.session == started.session)(current.entryOption(interaction)).flatten
    val cacheState = new RemoteHistoryCache.CacheStore[SyncContext, F, FormattedSyncDestination[F]] {
      def now() = inSession(info).getOrElse(entry).cache
      def update(transition: CacheState[SyncContext, F, FormattedSyncDestination[F]] => CacheState[SyncContext, F, FormattedSyncDestination[F]]): Unit =
        store.update(current => inSession(current).fold(current)(found => current.withEntry(found.copy(cache = transition(found.cache)))))
    }
    val strategies = entry.destinations(started.connection).map(destination => destination -> destination.information.syncInformation.syncStrategy).toMap
    operation(new RemoteHistoryCache(started.connection.logger, strategies, cacheState, InteractionEntry.validate(interaction, _)), entry)
  }

  // FEEDBACK

  def feedbackFor[F <: InteractionFormat](element: WorkbookInteractionElement[F], session: InteractionInfo.Session): InteractionScaffoldingAndGradingControl[F] =
    new InteractionScaffoldingAndGradingControl(new FeedbackTarget[F] {
      val interaction: WorkbookInteractionElement[F] = element
      def isCurrent: Boolean = info.session == session && info.contains(interaction)
      def currentHistory: InteractionVariableHistory[F] =
        if isCurrent then info.historyOf(interaction) else InteractionEntry.defaultHistory(interaction)
      def execution: ExecutionContextInformation = info.connection.execution
      def publish(transition: InteractionVariableHistory[F] => InteractionVariableHistory[F]): Unit =
        store.update(current => if current.session != session then current else current.withHistory(interaction)(transition).getOrElse(current))
    })
}

object InteractionController {
  /** A controller that applies every change to `store` at once. */
  def apply(store: State[InteractionInfo]): InteractionController = new InteractionController(store)

  /** For an empty initial state, before a controller exists to create feedback. */
  val noFeedback: InteractionInfo.FeedbackFactory = new InteractionInfo.FeedbackFactory {
    def feedbackFor[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], session: InteractionInfo.Session) =
      throw new IllegalStateException("An initial state of a controller has no interactions")
  }

  /** An empty offline state of `usageContext`, to be bound by a controller. */
  def emptyState(usageContext: UsageContext): InteractionInfo =
    InteractionInfo.create(usageContext, Seq.empty, SyncConnection.offline, noFeedback)
}
