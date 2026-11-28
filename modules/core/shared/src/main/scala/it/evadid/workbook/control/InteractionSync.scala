package it.evadid.workbook.control

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.storage.remote.RemoteHistoryCache
import it.evadid.core.datastructures.storage.remote.RemoteHistoryCache.{CacheState, CacheStore}
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.model.{InteractionEntry, InteractionInfo, InteractionScope}
import it.evadid.workbook.sync.{FormattedSyncDestination, SyncContext, SyncResetState}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

/** Synchronization of the answers in `store` with the destinations of their connection, owned by an
  * [[InteractionController]] (its `sync`).
  *
  * Changed histories are stored when `schedule` runs the store, so changes made together share one write.
  * Phases keep the state they started with; the requests of one connection share its buffered destinations. A reset
  * pauses synchronization of its user, across workbooks, until it has succeeded; `rebind` restores the defaults.
  */
final class InteractionSync private[control] (store: State[InteractionInfo], schedule: (() => Unit) => Unit,
    rebind: InteractionScope => Unit) {
  private given ExecutionContext = ExecutionContext.global
  private type AnyInteraction = WorkbookInteractionElement[? <: InteractionFormat]

  private def info: InteractionInfo = store.now()
  private def logger: SyncLogger = info.connection.logger

  // PENDING STORES

  /** Schedules a store of the changed history under `key` of the current session; requests until it runs join it. */
  private[control] def requestStore(key: String): Unit = {
    val pending = (info.session, key)
    val added = pendingStores.synchronized {
      val absent = !pendingStores.now().contains(pending)
      if absent then pendingStores.update(_ + pending)
      absent
    }
    if added then schedule(() => runPendingStores(Set(pending)))
  }

  private val pendingStores = State(Set.empty[(InteractionInfo.Session, String)])

  /** Runs pending stores of the current session once: when due or, earlier, before it is replaced or reconnected. */
  private def runPendingStores(only: Set[(InteractionInfo.Session, String)]): Unit = {
    val current = info
    val due = pendingStores.synchronized {
      val selected = pendingStores.now().intersect(only)
      pendingStores.update(_ -- selected)
      selected
    }
    if due.nonEmpty then batch(current, due.filter(_._1 == current.session).map(_._2).toSeq, forceFlush = false)(storeEntry(_, state = current))
  }

  /** Runs the pending stores at once and sends every buffered request of the current connection, e.g. before the
    * session is replaced or the page is closed.
    */
  def sendPending(): Unit = {
    runPendingStores(pendingStores.now())
    Try(flush()).failed.foreach(error => logger.logExceptionWarn("Could not send buffered histories", error))
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
  def storeEntry(key: String, forceFlush: Boolean = false, state: InteractionInfo = info): Future[Unit] =
    onEntry(key, state)([F <: InteractionFormat] => (cache: EntryCache[F], _: InteractionEntry[F]) => cache.storeAll(forceFlush))

  def fetchEntry(key: String, forceFlush: Boolean = false, state: InteractionInfo = info): Future[Unit] =
    onEntry(key, state)([F <: InteractionFormat] => (cache: EntryCache[F], entry: InteractionEntry[F]) =>
      SyncPhases.settled(entry.destinations(state.connection).toSeq.map(destination => SyncPhases.attempt(
        cache.refreshFrom(destination, Set(entry.syncContext), Instant.now().minus(java.time.Duration.ofMinutes(5)), forceFlush)).map(_ => ()))))

  /** Sends the buffered requests of every destination of the current connection. */
  def flush(): Unit = info.connection.flush()

  // PHASES

  def storeAll(forceFlush: Boolean = false): Future[Unit] = {
    val state = info
    batch(state, state.entries.keys.toSeq, forceFlush)(storeEntry(_, state = state))
  }

  def fetchAll(forceFlush: Boolean = false): Future[Unit] = {
    val state = info
    batch(state, state.entries.keys.toSeq, forceFlush)(fetchEntry(_, state = state))
  }

  /** Reads, then writes, the state current at the start, even if another state is selected meanwhile. */
  def fetchAndStore(forceFlush: Boolean = false): Future[Unit] = {
    val state = info
    val keys = if resetStateOf(userOf(state)) == SyncResetState.Idle then state.entries.keys.toSeq else Nil
    SyncPhases.inOrder(batch(state, keys, forceFlush)(fetchEntry(_, state = state)), batch(state, keys, forceFlush)(storeEntry(_, state = state)))
  }

  /** Reads and writes everything with forced flushes; during a reset, waits for it instead. */
  def ensureSynced(): Future[Unit] = resetState match {
    case SyncResetState.Running(completion) => completion
    case SyncResetState.Idle => fetchAndStore(forceFlush = true)
    case _ => Future.unit
  }

  /** Explicit synchronization of one interaction (e.g. its sync icon): writes and flushes its pending events, then
    * reads it back, since only events read from a destination count as synchronized.
    */
  def synchronize(interaction: AnyInteraction): Future[Unit] = {
    val state = info
    val keys = Seq(interaction.keyForSerialization).filter(_ => state.contains(interaction))
    SyncPhases.inOrder(batch(state, keys, forceFlush = true)(storeEntry(_, state = state)),
      batch(state, keys, forceFlush = true)(fetchEntry(_, state = state)))
  }

  /** Runs `operation` for `keys` of `state`, then flushes if forced; nothing runs while the user's reset is pending.
    * Each entry registers its requests with the shared buffers, so a forced phase sends one request per destination.
    */
  private def batch(state: InteractionInfo, keys: Seq[String], forceFlush: Boolean)(operation: String => Future[Unit]): Future[Unit] = {
    val user = userOf(state)
    if resetStateOf(user) != SyncResetState.Idle then Future.unit else {
      val operations = keys.map(key => SyncPhases.attempt(operation(key)))
      val flushing = if forceFlush then SyncPhases.attempt { state.connection.flush(); Future.unit } else Future.unit
      track(user, SyncPhases.settled(operations :+ flushing))
    }
  }

  // RESET

  /** Deletes the user's histories in every destination of the current connection, across workbooks. The answers are
    * bound anew from their defaults at once; until the reset has succeeded, synchronization stays paused for the user.
    */
  def resetAllData(): Future[Unit] = {
    val state = info
    val user = userOf(state)
    resetStateOf(user) match {
      case SyncResetState.Running(completion) => completion
      case _ =>
        val completion = Promise[Unit]()
        updateUser(user)(_.copy(reset = SyncResetState.Running(completion.future)))
        rebind(InteractionScope(state.usageContext, state.interactions, state.connection))
        val draining = users.now().get(user).fold(Set.empty)(_.pending).toSeq.map(_.recover { case scala.util.control.NonFatal(_) => () })
        val reset = SyncPhases.attempt {
          state.connection.flush()
          Future.sequence(draining).flatMap(_ => SyncPhases.settled(state.connection.destinations.toSeq.map(destination =>
            SyncPhases.attempt(destination.handler.clearAllHistories(destination.usageContext)).map(_ => ()))))
        }
        reset.onComplete { result =>
          updateUser(user)(_.copy(reset = result.fold(SyncResetState.Failed(_), _ => SyncResetState.Completed)))
          completion.tryComplete(result)
        }
        completion.future
    }
  }

  /** The reset progress of the current user. */
  def resetState: SyncResetState = resetStateOf(userOf(info))

  /** Follows [[resetState]] as the user or their reset progress changes. */
  lazy val resetStates: ObservableValue[SyncResetState] =
    store.observable.combineWith(users.observable).deriveValue((current, all) => all.get(userOf(current)).fold(SyncResetState.Idle)(_.reset))

  /** Reset progress and running operations per user: a reset waits for them and spans workbooks and credentials. */
  private case class UserSyncState(reset: SyncResetState = SyncResetState.Idle, pending: Set[Future[Unit]] = Set.empty)
  private val users = State(Map.empty[String, UserSyncState])
  private def userOf(state: InteractionInfo): String = state.usageContext.userId
  private def resetStateOf(user: String): SyncResetState = users.now().get(user).fold(SyncResetState.Idle)(_.reset)
  private def updateUser(user: String)(transition: UserSyncState => UserSyncState): Unit =
    users.update(all => all.updated(user, transition(all.getOrElse(user, UserSyncState()))))

  private def track(user: String, operation: Future[Unit]): Future[Unit] = {
    updateUser(user)(current => current.copy(pending = current.pending + operation))
    operation.onComplete(_ => updateUser(user)(current => current.copy(pending = current.pending - operation)))
    operation
  }

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
    new RemoteHistoryCache(entry.interaction.interactionFormat, started.connection.logger, strategies, cacheStore, history => InteractionEntry.validate(entry.interaction, history.events.values))
  }
}
