package it.evadid.workbook.interaction.binding

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.storage.remote.RemoteHistoryCache
import it.evadid.core.datastructures.storage.remote.RemoteHistoryCache.CacheState
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.interaction.sync.{FormattedSyncDestination, SyncContext, SyncInformationWithContext}
import it.evadid.workbook.interaction.variable.{InteractionScaffoldingAndGradingControl, InteractionVariable, InteractionVariableHistory}
import it.evadid.workbook.meta.{ExecutionContextInformation, InteractionVariableSyncReport}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

/** The history of one interaction in one sync context, together with what each destination is known to hold.
  *
  * A binding never changes its context; its holder replaces it instead, and the replacement starts from the default
  * answer. Only the connection can change: that keeps the history and forgets the knowledge of destinations that
  * are no longer connected, so results of a previous connection are ignored. Feedback is computed per binding and
  * only a current binding publishes it.
  */
final class InteractionBinding[F <: InteractionFormat] private[interaction] (
    val variable: InteractionVariable[F], val syncContext: SyncContext, initialConnection: SyncConnection) {
  private given ExecutionContext = ExecutionContext.global
  private type Destination = FormattedSyncDestination[F]
  private type History = InteractionVariableHistory[F]
  private def interaction = variable.underlyingInteraction

  private val connectionState = State(initialConnection)
  private val state: State[CacheState[SyncContext, F, Destination]] =
    State(CacheState(Map(syncContext -> variable.defaultHistory()), Map.empty[Destination, Map[SyncContext, RemoteHistoryCache.RemoteHistoryValue]])
      .trackingOnly(destinationsOf(initialConnection)))

  val control: InteractionScaffoldingAndGradingControl[F] = new InteractionScaffoldingAndGradingControl(this)

  def connection: SyncConnection = connectionState.now()
  def execution: ExecutionContextInformation = connection.execution
  def isCurrent: Boolean = variable.binding eq this

  def currentHistory: History = state.now().histories(syncContext)
  lazy val history: ObservableValue[History] = state.observable.deriveValue(_.histories(syncContext))

  /** All configured destinations and those holding the latest answer with all its feedback events. */
  lazy val report: ObservableValue[InteractionVariableSyncReport[SyncInformationWithContext]] =
    state.observable.combineWith(connectionState.observable).deriveValue((current, connected) =>
      InteractionVariableSyncReport(connected.destinations,
        current.confirmedDestinations(syncContext).intersect(destinationsOf(connected)).map(_.information)))

  /** Uses `next` for future requests; the history stays and only destinations kept by `next` stay known. */
  def connect(next: SyncConnection): Unit = variable.synchronized {
    if next != connection then {
      connectionState.set(next)
      state.update(_.trackingOnly(destinationsOf(next)))
    }
  }

  /** Applies `transition`; every answer must satisfy the exercise, and the history always has a current answer. */
  private[interaction] def updateHistory(transition: History => History): Unit = variable.synchronized {
    state.update { current =>
      val next = transition(current.histories(syncContext))
      validate(next).get
      val withAnswer = if next.historyStates.lastStateOption.isEmpty then next.mergeWith(variable.defaultHistory()) else next
      current.copy(histories = current.histories.updated(syncContext, withAnswer.cleanedHistory()))
    }
  }

  /** Records feedback only while this binding is current; results of a replaced binding are dropped. */
  private[interaction] def publish(transition: History => History): Unit = variable.synchronized {
    if isCurrent then variable.updateHistory(transition)
  }

  /** Writes the events each destination is missing; buffered destinations send them when flushed. */
  def store(forceFlush: Boolean = false): Future[Unit] = cache.storeAll(forceFlush)

  /** Reads new events from every destination, fully once the last full read is five minutes old. A read failure
    * is reported after the other destinations have been read. A changed answer is graded like an edit.
    */
  def fetch(forceFlush: Boolean = false): Future[Unit] = {
    val before = variable.currentValue
    val current = cache
    val reads = destinationsOf(connection).toSeq.map(destination => Try(current.refreshFrom(destination, Set(syncContext),
      LocalDateTime.now().minusMinutes(5), forceFlush)).fold(Future.failed, identity).transform(Success(_)))
    Future.sequence(reads).flatMap { results =>
      variable.synchronized(if isCurrent && variable.currentValue != before then control.getOrCalculateGrading())
      results.collectFirst { case scala.util.Failure(error) => error }.fold(Future.unit)(Future.failed)
    }
  }

  def flush(): Unit = connection.flush()

  private def validate(history: History): Try[Unit] =
    Try(history.events.values.foreach(event => interaction.validateState(event.associatedState)))

  private def destinationsOf(connected: SyncConnection): Set[Destination] =
    connected.destinations.map(information => FormattedSyncDestination(information, interaction.interactionFormat))

  private def cache: RemoteHistoryCache[SyncContext, F, Destination] = new RemoteHistoryCache(connection.logger,
    destinationsOf(connection).map(destination => destination -> destination.information.syncInformation.syncStrategy).toMap,
    state, validate)
}
