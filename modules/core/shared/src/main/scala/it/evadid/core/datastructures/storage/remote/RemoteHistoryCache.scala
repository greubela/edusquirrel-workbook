package it.evadid.core.datastructures.storage.remote

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.storage.remote.RemoteHistoryCache.*
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.interaction.sync.{EventSyncStrategy, SyncSuccess}
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.interaction.variable.events.{EventId, InteractionEvent, InteractionEvents}
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/** Typed additive cache used by workbook synchronization.
  *
  * Each destination is tracked by the IDs of the immutable events it is known to hold. An event becomes known when a
  * read returns it or when a committed write acknowledges it ([[SyncSuccess.elementsStored]]); a requested write
  * alone is not evidence. A store sends the strategy's selection minus the known events; the buffered writer merges
  * repeated requests until it flushes. Lost or failed writes stay unconfirmed and are resent.
  *
  * Results for a destination that the state no longer tracks are ignored, so a replaced connection cannot change
  * its successor. A received history that `validate` rejects is reported and discarded.
  */
final class RemoteHistoryCache[K, F <: InteractionFormat, RW <: RemoteHistoryReaderWriter[K, F]](
    syncLogger: SyncLogger, cacheStrategies: Map[RW, EventSyncStrategy],
    state: State[CacheState[K, F, RW]], validate: InteractionVariableHistory[F] => Try[Unit] = (_: InteractionVariableHistory[F]) => Success(())) {
  private given ExecutionContext = ExecutionContext.global
  private type History = InteractionVariableHistory[F]

  lazy val updateReport: ObservableValue[Map[K, History]] = state.observable.deriveValue(_.histories)
  lazy val reports: ObservableValue[Map[K, CacheReportForKey[K, F, RW]]] = state.observable.deriveValue { current =>
    current.histories.map { (key, history) =>
      val known = current.destinations.flatMap((cache, values) => values.get(key).map(cache -> _))
      def knownIds(cache: RW): Set[EventId] = known.get(cache).fold(Set.empty)(_.knownEventIds)
      val pending = cacheStrategies.map((cache, strategy) => cache -> missing(history, knownIds(cache), strategy))
      key -> CacheReportForKey(key, history, known, pending, current.confirmedDestinations(key).intersect(cacheStrategies.keySet))
    }
  }

  private def attempt[A](operation: => Future[A]): Future[A] = Try(operation).fold(Future.failed, identity)
  private def empty: History = InteractionVariableHistory(InteractionEvents.empty)
  private def later(a: Option[LocalDateTime], b: Option[LocalDateTime]): Option[LocalDateTime] = (a.toList ++ b).maxOption
  private def missing(local: History, known: Set[EventId], strategy: EventSyncStrategy): History =
    InteractionVariableHistory(InteractionEvents(local.cleanWithStrategy(strategy).events.values.filterNot(event => known(event.eventId))))

  /** Events are immutable; a received event whose ID names different local content is corrupt and left out. */
  private def withoutConflicts(cache: RW, key: K, received: Seq[InteractionEvent[F]]): Set[InteractionEvent[F]] = {
    val local = state.now().histories.get(key).fold(Map.empty[EventId, InteractionEvent[F]])(_.events.values.map(event => event.eventId -> event).toMap)
    val (conflicting, accepted) = received.toSet.partition(event => local.get(event.eventId).exists(_ != event))
    if conflicting.nonEmpty then syncLogger.logWarn(
      s"Discarded ${conflicting.size} events from $cache whose IDs name different local events: ${conflicting.map(_.eventId).mkString(", ")}")
    accepted
  }


  def mergeLocal(histories: Map[K, History]): Unit = state.update(_.withLocalHistories(histories))

  /** Freshness is measured by successful tail-read completion, independently of event timestamps and write acknowledgements. */
  def ensureMaxAge(keys: Set[K], maximumAcceptableAge: LocalDateTime = LocalDateTime.now(),
      forceFlush: Boolean = false): Future[Unit] = attempt {
    if forceFlush then flush(true)
    val current = state.now()
    val requests = cacheStrategies.keys.toList.flatMap { cache =>
      val outdated = keys.filter(key => current.knownAt(cache, key)
        .flatMap(_.fetchCompleted).forall(_.isBefore(maximumAcceptableAge)))
      outdated.groupBy(key => current.knownAt(cache, key).flatMap(_.fetchedThrough))
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
    val current = state.now()
    val windows = keys.groupBy { key =>
      current.knownAt(destination, key).filter(_.fullFetchCompleted.exists(time => !time.isBefore(fullRefreshBefore)))
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
          key -> InteractionVariableHistory(InteractionEvents(withoutConflicts(cache, key, bounded)))
        }.filter((key, history) => validate(history).fold(error => {
          syncLogger.logExceptionWarn(s"Discarded the history of $key received from $cache: the exercise rejects one of its answers", error)
          false
        }, _ => true)).toMap
        state.update(withFetched(_, cache, received, completed, trackProgress, fullRead = starting.isEmpty))
        received
      }
    }
  }.andThen { case Failure(error) => syncLogger.logExceptionWarn(s"Could not fetch event histories for ${keys.size} keys", error) }

  /** Merges a read into the local and destination histories; only tracked reads advance the destination's progress.
    * A read from a destination the state no longer tracks changes nothing.
    */
  private def withFetched(current: CacheState[K, F, RW], cache: RW, received: Map[K, History], completed: LocalDateTime,
      trackProgress: Boolean, fullRead: Boolean): CacheState[K, F, RW] = current.destinations.get(cache).fold(current) { tracked =>
    val remote = received.foldLeft(tracked) { case (values, (key, history)) =>
      val previous = values.getOrElse(key, RemoteHistoryValue.empty)
      values.updated(key, previous.copy(
        knownEventIds = previous.knownEventIds ++ history.events.ids,
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
      val known = current.knownAt(cache, key).fold(Set.empty)(_.knownEventIds)
      val delta = missing(current.histories(key), known, cacheStrategies(cache))
      Option.when(delta.events.values.nonEmpty)(key -> delta)
    }.toMap
    if deltas.isEmpty then Future.unit else {
      val written = cache.writer.writeAll(syncLogger, deltas)
      cache.writer.flush(syncLogger, forceFlush)
      written.map(success => { state.update(_.withStored(cache, deltas.map((key, delta) => key -> delta.events.ids), success.elementsStored)); () })
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

  /** What one destination is known to hold for a key; the progress fields advance only with tracked reads. */
  case class RemoteHistoryValue(
      knownEventIds: Set[EventId],
      fetchCompleted: Option[LocalDateTime],
      fetchedThrough: Option[LocalDateTime],
      fullFetchCompleted: Option[LocalDateTime])

  object RemoteHistoryValue {
    val empty: RemoteHistoryValue = RemoteHistoryValue(Set.empty, None, None, None)
  }

  case class CacheState[K, F <: InteractionFormat, RW](
      histories: Map[K, InteractionVariableHistory[F]],
      destinations: Map[RW, Map[K, RemoteHistoryValue]]) {
    def withLocalHistories(incoming: Map[K, InteractionVariableHistory[F]]): CacheState[K, F, RW] =
      copy(histories = incoming.foldLeft(histories) { case (merged, (key, history)) =>
        merged.updated(key, merged.getOrElse(key, InteractionVariableHistory(InteractionEvents.empty[F])).mergeWith(history).cleanedHistory())
      })

    def knownAt(cache: RW, key: K): Option[RemoteHistoryValue] = destinations.get(cache).flatMap(_.get(key))

    /** Tracked destinations known to hold the latest answer of `key` and all its feedback events. */
    def confirmedDestinations(key: K): Set[RW] = {
      val required = histories.get(key).fold(Set.empty[EventId])(_.latestAnswerEvents.map(_.eventId))
      destinations.keySet.filter(cache => required.subsetOf(knownAt(cache, key).fold(Set.empty)(_.knownEventIds)))
    }

    /** Tracks exactly `tracked`: knowledge of kept destinations stays, removed ones are forgotten, new ones start empty. */
    def trackingOnly(tracked: Set[RW]): CacheState[K, F, RW] =
      copy(destinations = tracked.map(cache => cache -> destinations.getOrElse(cache, Map.empty)).toMap)

    /** Registers the sent events that a committed write acknowledged as stored; an untracked destination is ignored. */
    def withStored(cache: RW, sent: Map[K, Set[EventId]], stored: Set[EventId]): CacheState[K, F, RW] =
      destinations.get(cache).fold(this)(tracked => copy(destinations = destinations.updated(cache, sent.foldLeft(tracked) { case (values, (key, ids)) =>
        val previous = values.getOrElse(key, RemoteHistoryValue.empty)
        values.updated(key, previous.copy(knownEventIds = previous.knownEventIds ++ ids.intersect(stored)))
      })))
  }

  object CacheState {
    def empty[K, F <: InteractionFormat, RW](destinations: Set[RW]): CacheState[K, F, RW] =
      CacheState(Map.empty, destinations.map(_ -> Map.empty[K, RemoteHistoryValue]).toMap)
  }

  case class CacheReportForKey[K, F <: InteractionFormat, RW](
      key: K,
      localHistory: InteractionVariableHistory[F],
      lastKnownValues: Map[RW, RemoteHistoryValue],
      pendingWrites: Map[RW, InteractionVariableHistory[F]],
      syncedDestinations: Set[RW])
}
