package it.evadid.workbook.interaction.state

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.storage.remote.RemoteHistoryCache
import it.evadid.core.datastructures.storage.remote.RemoteHistoryCache.{CacheState, CacheStore}
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.interaction.sync.{FormattedSyncDestination, SyncConnection, SyncContext, SyncInformationWithContext, SyncPhases, UpdateImportance}
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import it.evadid.workbook.interaction.variable.InteractionVariableState.InteractionVariableStateChanged
import it.evadid.workbook.interaction.variable.{FeedbackTarget, InteractionScaffoldingAndGradingControl, InteractionVariableHistory, InteractionVariableState}
import it.evadid.workbook.meta.{ExecutionContextInformation, InteractionVariableSyncReport}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/** Every change of the [[InteractionInfo]] held by `store`. Writes are pure transitions of the whole state, so
  * they compose even where the store applies them later (an Airstream transaction). Reactions run once a change is
  * observed: a changed answer is graded by the graders that run automatically for it, and a changed history is
  * stored through [[requestStore]], which a front end may defer so that changes made together share one write.
  */
class InteractionController(store: State[InteractionInfo]) {
  private given ExecutionContext = ExecutionContext.global
  private type AnyInteraction = WorkbookInteractionElement[? <: InteractionFormat]
  private type Control = InteractionScaffoldingAndGradingControl[? <: InteractionFormat]

  def info: InteractionInfo = store.now()
  def logger: SyncLogger = info.connection.logger

  // OBSERVED CHANGES

  private val observed = State(store.now())
  store.observable.addObserver { next =>
    val previous = observed.now()
    observed.set(next)
    afterChange(previous, next)
  }

  /** Reactions to a change within one session; a new session starts without them. */
  private def afterChange(previous: InteractionInfo, next: InteractionInfo): Unit =
    if previous.session == next.session then next.entries.foreach { (key, entry) =>
      previous.entries.get(key).filter(_ ne entry).foreach { before =>
        if before.history != entry.history then requestStore(key)
        val current = entry.currentState
        if before.currentValue != current.value && entry.interaction.gradingElements.exists(_.shouldRunGrader(false, current.updateImportance)) then
          feedback(entry.interaction).getOrCalculateGrading()
      }
    }

  /** Stores the changed history under `key` of the current session; stores at once by default. */
  protected def requestStore(key: String): Unit = storeEntry(key)

  // READS

  def currentHistory[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): InteractionVariableHistory[F] = info.historyOf(interaction)
  def currentState[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): InteractionVariableState[InteractionFormat.State[F]] =
    info.currentStateOf(interaction)
  def currentValue[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): InteractionFormat.State[F] = info.currentValueOf(interaction)
  def report(interaction: AnyInteraction): InteractionVariableSyncReport[SyncInformationWithContext] = info.reportOf(interaction)

  // TRANSITIONS

  /** Starts a new session with the default answers of `scope`; the previous state is returned. */
  def bind(scope: InteractionScope): InteractionInfo = {
    val previous = info
    store.set(InteractionInfo.create(scope))
    previous
  }

  /** The same answers with another connection. */
  def connect(connection: SyncConnection): Unit = store.update(_.connectedTo(connection))

  /** Follows the page to `scope`, which is read when the store applies the change, after earlier changes of the
    * same transaction. The same context with the same interaction instances keeps its answers and, if `reconnect`,
    * takes the scope's connection; anything else starts a new session with default answers.
    */
  def follow(scope: () => InteractionScope, reconnect: Boolean): Unit = store.update { current =>
    val next = scope()
    if !current.holdsAnswersOf(next) then InteractionInfo.create(next)
    else if reconnect then current.connectedTo(next.connection)
    else current
  }

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
      transform => store.update(current => orUnchanged(current, interaction,
        current.withUserChange(interaction, transform(current.currentValueOf(interaction)), _ => importance))),
      store.observable.deriveValue(_.currentValueOf(interaction)))

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
  def storeEntry(key: String, forceFlush: Boolean = false, state: InteractionInfo = info): Future[Unit] =
    onEntry(key, state)([F <: InteractionFormat] => (cache: EntryCache[F], _: InteractionEntry[F]) => cache.storeAll(forceFlush))

  def fetchEntry(key: String, forceFlush: Boolean = false, state: InteractionInfo = info): Future[Unit] =
    onEntry(key, state)([F <: InteractionFormat] => (cache: EntryCache[F], entry: InteractionEntry[F]) =>
      SyncPhases.settled(entry.destinations(state.connection).toSeq.map(destination => SyncPhases.attempt(
        cache.refreshFrom(destination, Set(entry.syncContext), LocalDateTime.now().minusMinutes(5), forceFlush)).map(_ => ()))))

  /** Sends the buffered requests of every destination of the current connection. */
  def flush(): Unit = info.connection.flush()

  private type EntryCache[F <: InteractionFormat] = RemoteHistoryCache[SyncContext, F, FormattedSyncDestination[F]]

  /** Runs `operation` on the cache of the entry under `key` in `state`. The cache reads the entry as the store holds
    * it while `state`'s session is current, and as `state` held it otherwise; its updates reach only that session,
    * and updates for destinations the session no longer uses are ignored.
    */
  private def onEntry(key: String, state: InteractionInfo)(
      operation: [F <: InteractionFormat] => (EntryCache[F], InteractionEntry[F]) => Future[Unit]): Future[Unit] =
    SyncPhases.attempt(state.entries.get(key).fold(Future.unit)(entry => operation(cacheOf(state, entry), entry)))

  private def cacheOf[F <: InteractionFormat](started: InteractionInfo, entry: InteractionEntry[F]): EntryCache[F] = {
    def inSession(current: InteractionInfo): Option[InteractionEntry[F]] =
      Option.when(current.session == started.session)(current.entryOption(entry.interaction)).flatten
    val cacheStore = new CacheStore[SyncContext, F, FormattedSyncDestination[F]] {
      def now(): CacheState[SyncContext, F, FormattedSyncDestination[F]] = inSession(info).getOrElse(entry).cache
      def update(transition: CacheState[SyncContext, F, FormattedSyncDestination[F]] => CacheState[SyncContext, F, FormattedSyncDestination[F]]): Unit =
        store.update(current => inSession(current).fold(current)(found => current.withEntry(found.copy(cache = transition(found.cache)))))
    }
    val strategies = entry.destinations(started.connection).map(destination => destination -> destination.information.syncInformation.syncStrategy).toMap
    new RemoteHistoryCache(started.connection.logger, strategies, cacheStore, history => InteractionEntry.validate(entry.interaction, history.events.values))
  }

  // FEEDBACK

  /** Feedback computations of the current session, created on first use; a new session starts without them. */
  private val feedbacks = State(Map.empty[(InteractionInfo.Session, String), Control])

  /** The feedback of `interaction` in the current session. Requests for an interaction outside it fail. */
  def feedback[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): InteractionScaffoldingAndGradingControl[F] = feedbacks.synchronized {
    val session = info.session
    val key = (session, interaction.keyForSerialization)
    // Instance identity guarantees that the control has the interaction's format.
    feedbacks.now().get(key).filter(_.interaction eq interaction).map(_.asInstanceOf[InteractionScaffoldingAndGradingControl[F]]).getOrElse {
      val created = new InteractionScaffoldingAndGradingControl(feedbackTarget(interaction, session))
      feedbacks.update(all => all.filter((known, _) => known._1 == session).updated(key, created))
      created
    }
  }

  private def feedbackTarget[F <: InteractionFormat](element: WorkbookInteractionElement[F], session: InteractionInfo.Session): FeedbackTarget[F] =
    new FeedbackTarget[F] {
      val interaction: WorkbookInteractionElement[F] = element
      def isCurrent: Boolean = info.session == session && info.contains(interaction)
      def currentHistory: InteractionVariableHistory[F] =
        if isCurrent then info.historyOf(interaction) else InteractionEntry.defaultHistory(interaction)
      def execution: ExecutionContextInformation = info.connection.execution
      def publish(transition: InteractionVariableHistory[F] => InteractionVariableHistory[F]): Unit =
        store.update(current => if current.session != session then current else orUnchanged(current, interaction, current.withHistory(interaction)(transition)))
    }
}

object InteractionController {
  /** A controller that applies every change to `store` at once. */
  def apply(store: State[InteractionInfo]): InteractionController = new InteractionController(store)
}
