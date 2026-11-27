package it.evadid.core.datastructures.storage.remote

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.storage.remote.RemoteHistoryCache.*
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.interaction.sync.EventSyncStrategy
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.interaction.variable.events.{InteractionEvent, InteractionEvents}
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

/** Typed additive cache used by workbook synchronization.
  *
  * A destination's known events come only from reads: a requested write is not evidence that the destination
  * stored it. A store sends the strategy's selection minus the events already read from that destination and
  * returns; the buffered writer merges repeated requests until it flushes. A later read (the periodic
  * synchronization or an explicit sync) confirms what arrived, so lost writes stay unconfirmed and are resent.
  */
final class RemoteHistoryCache[K, F <: InteractionFormat, RW <: RemoteHistoryReaderWriter[K, F]](
    syncLogger: SyncLogger, cacheStrategies: Map[RW, EventSyncStrategy],
    state: State[CacheState[K, F, RW]]) {
  private given ExecutionContext = ExecutionContext.global
  private type History = InteractionVariableHistory[F]

  val updateReport: ObservableValue[Map[K, History]] = state.observable.deriveValue(_.histories)
  val reports: ObservableValue[Map[K, CacheReportForKey[K, F, RW]]] = state.observable.deriveValue { current =>
    current.histories.map { (key, history) =>
      val known = current.destinations.flatMap((cache, values) => values.get(key).map(cache -> _))
      def knownHistory(cache: RW): History = known.getOrElse(cache, RemoteHistoryValue.empty).history
      val pending = cacheStrategies.map((cache, strategy) => cache -> missing(history, knownHistory(cache), strategy))
      val required = history.latestAnswerEvents
      val synced = cacheStrategies.keySet.filter(cache => required.subsetOf(knownHistory(cache).events.values))
      key -> CacheReportForKey(key, history, known, pending, synced)
    }
  }

  private def attempt[A](operation: => Future[A]): Future[A] = Try(operation).fold(Future.failed, identity)
  private def empty: History = InteractionVariableHistory(InteractionEvents.empty)
  private def later(a: Option[LocalDateTime], b: Option[LocalDateTime]): Option[LocalDateTime] = (a.toList ++ b).maxOption
  private def missing(local: History, known: History, strategy: EventSyncStrategy): History =
    InteractionVariableHistory(InteractionEvents(local.cleanWithStrategy(strategy).events.values -- known.events.values))


  def mergeLocal(histories: Map[K, History]): Unit = state.update(_.withLocalHistories(histories))

  /** Freshness is measured by successful tail-read completion, independently of event timestamps and write acknowledgements. */
  def ensureMaxAge(keys: Set[K], maximumAcceptableAge: LocalDateTime = LocalDateTime.now(),
      forceFlush: Boolean = false): Future[Unit] = attempt {
    if forceFlush then flush(true)
    val current = state.now()
    val requests = cacheStrategies.keys.toList.flatMap { cache =>
      val outdated = keys.filter(key => current.destinations(cache).get(key)
        .flatMap(_.fetchCompleted).forall(_.isBefore(maximumAcceptableAge)))
      outdated.groupBy(key => current.destinations(cache).get(key).flatMap(_.fetchedThrough))
        .toList.map((cursor, selected) => (cache, selected, cursor))
    }
    Future.traverse(requests)((cache, selected, cursor) =>
      fetch(cache, selected, cursor, LocalDateTime.MAX, forceFlush, trackProgress = true)).map(_ => ())
  }

  /** Query explicit windows without claiming the unread portions of a history have been refreshed. */
  def fetchEventsBetween(keys: Seq[K], starting: LocalDateTime, until: LocalDateTime,
      forceFlush: Boolean = false): Future[Map[K, Seq[InteractionEvent[F]]]] = attempt {
    require(!until.isBefore(starting), "History range end must not precede its start")
    if forceFlush then flush(true)
    Future.traverse(cacheStrategies.keys)(cache =>
      fetch(cache, keys.toSet, Some(starting), until, forceFlush, trackProgress = false)).map(combine)
  }

  /** A successful destination can restore its events independently of unavailable peers. */
  def fetchAllFrom(destination: RW, keys: Set[K], forceFlush: Boolean = false): Future[Map[K, Seq[InteractionEvent[F]]]] = attempt {
    require(cacheStrategies.contains(destination), "Cannot read an unconfigured history destination")
    fetch(destination, keys, None, LocalDateTime.MAX, forceFlush, trackProgress = true)
      .map(response => combine(List(response)))
  }

  /** Normal polls replay the last event; periodic full reads also recover late uploads with older timestamps. */
  def refreshFrom(destination: RW, keys: Set[K], fullRefreshBefore: LocalDateTime,
      forceFlush: Boolean = false): Future[Map[K, Seq[InteractionEvent[F]]]] = attempt {
    require(cacheStrategies.contains(destination), "Cannot read an unconfigured history destination")
    val known = state.now().destinations(destination)
    val windows = keys.groupBy { key =>
      known.get(key).filter(_.fullFetchCompleted.exists(time => !time.isBefore(fullRefreshBefore)))
        .flatMap(value => value.fetchedThrough.orElse(value.fetchCompleted))
    }
    Future.traverse(windows.toSeq) { (cursor, selected) =>
      fetch(destination, selected, cursor, LocalDateTime.MAX, forceFlush, trackProgress = true)
    }.map(combine)
  }

  def fetchAll(keys: Set[K], forceFlush: Boolean = false): Future[Map[K, Seq[InteractionEvent[F]]]] = attempt {
    if forceFlush then flush(true)
    Future.traverse(cacheStrategies.keys)(cache =>
      fetch(cache, keys, None, LocalDateTime.MAX, forceFlush, trackProgress = true)).map(combine)
  }

  private def combine(responses: Iterable[Map[K, History]]): Map[K, Seq[InteractionEvent[F]]] =
    responses.flatMap(_.toList).groupMapReduce(_._1)(_._2)(_.mergeWith(_))
      .map((key, history) => key -> history.events.values.toList.sortBy(_.timestamp))

  private def fetch(cache: RW, keys: Set[K], starting: Option[LocalDateTime], until: LocalDateTime,
      forceFlush: Boolean, trackProgress: Boolean): Future[Map[K, History]] = attempt {
    if keys.isEmpty then Future.successful(Map.empty) else {
      val strategy = cacheStrategies(cache)
      val pending = starting match {
        case None => cache.reader.fetchAll(syncLogger, keys, strategy)
        case Some(from) => cache.reader.fetchEventsBetween(syncLogger, keys.toSeq, from, until, strategy)
      }
      cache.reader.flush(syncLogger, forceFlush)
      pending.map { response =>
        val completed = LocalDateTime.now()
        val received = keys.map { key =>
          val bounded = response.getOrElse(key, Nil).filter(event =>
            starting.forall(from => !event.timestamp.isBefore(from)) && !event.timestamp.isAfter(until))
          key -> InteractionVariableHistory(InteractionEvents(bounded.toSet))
        }.toMap
        state.update(withFetched(_, cache, received, completed, trackProgress, fullRead = starting.isEmpty))
        received
      }
    }
  }.andThen { case Failure(error) => syncLogger.logExceptionWarn(s"Could not fetch event histories for ${keys.size} keys", error) }

  /** Merges a read into the local and destination histories; only tracked reads advance the destination's progress. */
  private def withFetched(current: CacheState[K, F, RW], cache: RW, received: Map[K, History], completed: LocalDateTime,
      trackProgress: Boolean, fullRead: Boolean): CacheState[K, F, RW] = {
    val remote = received.foldLeft(current.destinations(cache)) { case (values, (key, history)) =>
      val previous = values.getOrElse(key, RemoteHistoryValue.empty)
      values.updated(key, previous.copy(
        history = previous.history.mergeWith(history),
        fullFetchCompleted = if trackProgress && fullRead then later(previous.fullFetchCompleted, Some(completed)) else previous.fullFetchCompleted,
        fetchCompleted = if trackProgress then later(previous.fetchCompleted, Some(completed)) else previous.fetchCompleted,
        fetchedThrough = if trackProgress then later(previous.fetchedThrough, history.events.timestampLastChanged) else previous.fetchedThrough))
    }
    val histories = received.foldLeft(current.histories) { case (histories, (key, history)) =>
      histories.updated(key, histories.getOrElse(key, empty).mergeWith(history).cleanedHistory())
    }
    current.copy(histories = histories, destinations = current.destinations.updated(cache, remote))
  }

  def requestStore(cache: RW, histories: Map[K, History], forceFlush: Boolean = false): Future[Unit] = attempt {
    require(cacheStrategies.contains(cache), "Cannot store to an unconfigured history destination")
    mergeLocal(histories)
    if forceFlush then flush(true)
    // Inputs belong to this request, even when the backing store defers its notification/update.
    val current = state.now().withLocalHistories(histories)
    val deltas = histories.keys.flatMap { key =>
      val known = current.destinations(cache).getOrElse(key, RemoteHistoryValue.empty).history
      val delta = missing(current.histories(key), known, cacheStrategies(cache))
      Option.when(delta.events.values.nonEmpty)(key -> delta)
    }.toMap
    if deltas.isEmpty then Future.unit else {
      val written = cache.writer.writeAll(syncLogger, deltas)
      cache.writer.flush(syncLogger, forceFlush)
      written.map(_ => ())
    }
  }.andThen { case Failure(error) => syncLogger.logExceptionWarn(s"Could not store event histories for ${histories.size} keys", error) }

  def requestStoreToAll(histories: Map[K, History], forceFlush: Boolean = false): Future[Unit] = {
    mergeLocal(histories)
    Future.traverse(cacheStrategies.keys)(cache => requestStore(cache, histories, forceFlush)).map(_ => ())
  }

  def storeAll(forceFlush: Boolean = false): Future[Unit] = requestStoreToAll(state.now().histories, forceFlush)

  def flush(forceFlush: Boolean = true): Unit = cacheStrategies.keys.foreach { cache =>
    cache.writer.flush(syncLogger, forceFlush)
    cache.reader.flush(syncLogger, forceFlush)
  }
}

object RemoteHistoryCache {
  def apply[K, F <: InteractionFormat, RW <: RemoteHistoryReaderWriter[K, F]](
      logger: SyncLogger, strategies: Map[RW, EventSyncStrategy]): RemoteHistoryCache[K, F, RW] =
    new RemoteHistoryCache(logger, strategies, State(CacheState.empty(strategies.keySet)))

  case class RemoteHistoryValue[F <: InteractionFormat](
      history: InteractionVariableHistory[F],
      fetchCompleted: Option[LocalDateTime],
      fetchedThrough: Option[LocalDateTime],
      fullFetchCompleted: Option[LocalDateTime])

  object RemoteHistoryValue {
    def empty[F <: InteractionFormat]: RemoteHistoryValue[F] =
      RemoteHistoryValue(InteractionVariableHistory(InteractionEvents.empty), None, None, None)
  }

  case class CacheState[K, F <: InteractionFormat, RW](
      histories: Map[K, InteractionVariableHistory[F]],
      destinations: Map[RW, Map[K, RemoteHistoryValue[F]]]) {
    def withLocalHistories(incoming: Map[K, InteractionVariableHistory[F]]): CacheState[K, F, RW] =
      copy(histories = incoming.foldLeft(histories) { case (merged, (key, history)) =>
        merged.updated(key, merged.getOrElse(key, InteractionVariableHistory(InteractionEvents.empty[F])).mergeWith(history).cleanedHistory())
      })
  }

  object CacheState {
    def empty[K, F <: InteractionFormat, RW](destinations: Set[RW]): CacheState[K, F, RW] =
      CacheState(Map.empty, destinations.map(_ -> Map.empty[K, RemoteHistoryValue[F]]).toMap)
  }

  case class CacheReportForKey[K, F <: InteractionFormat, RW](
      key: K,
      localHistory: InteractionVariableHistory[F],
      lastKnownValues: Map[RW, RemoteHistoryValue[F]],
      pendingWrites: Map[RW, InteractionVariableHistory[F]],
      syncedDestinations: Set[RW])
}
