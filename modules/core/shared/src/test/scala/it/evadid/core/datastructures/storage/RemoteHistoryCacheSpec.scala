package it.evadid.core.datastructures.storage

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.storage.remote.{RemoteHistoryCache, RemoteHistoryDataReader, RemoteHistoryDataWriter, RemoteHistoryReaderWriter}
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.{PrintToStdLogger, SyncLogger}
import it.evadid.workbook.interaction.sync.{EventSyncStrategy, SyncContext, SyncSuccess, UpdateImportance, UsageContext}
import it.evadid.workbook.interaction.variable.events.*
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import java.time.LocalDateTime
import munit.FunSuite
import scala.concurrent.{ExecutionContext, Future, Promise}

import it.evadid.workbook.abstractions.InteractionFormat.TextInteractionFormat

class RemoteHistoryCacheSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private type Event = InteractionEvent[TextInteractionFormat.type]
  private type History = InteractionVariableHistory[TextInteractionFormat.type]
  private type Cache = RemoteHistoryCache[SyncContext, TextInteractionFormat.type, Destination]
  private val logger = SyncLogger(Logger.withNameAndPrefixes(printMap = PrintToStdLogger.printNothing))
  private val time = LocalDateTime.of(2026, 11, 16, 12, 0)
  private val key = UsageContext("program", "workbook", "user").toSyncContext("answer")
  private val otherKey = key.copy(keyForSerialisation = "other")
  private val empty: History = InteractionVariableHistory.empty[TextInteractionFormat.type]
  private def event(value: Int, seconds: Long, importance: UpdateImportance = UpdateImportance.MAJOR): Event =
    AnswerChangedEvent[TextInteractionFormat.type](value.toString, time.plusSeconds(seconds), importance, EventId(new java.util.UUID(0L, s"$value/$seconds/$importance".hashCode.toLong).toString))
  private def ids(events: Event*): Set[EventId] = events.map(_.eventId).toSet
  private def history(events: Event*): History = InteractionVariableHistory(InteractionEvents(events.toSet))
  private case class Read(keys: Seq[SyncContext], starting: LocalDateTime, until: LocalDateTime, strategy: EventSyncStrategy)

  private class Destination extends RemoteHistoryReaderWriter[SyncContext, TextInteractionFormat.type] {
    val persisted = State(Map.empty[SyncContext, History])
    val reads = State(List.empty[Read])
    val writes = State(List.empty[Map[SyncContext, History]])
    val readOperation = State[Read => Future[Map[SyncContext, List[Event]]]](request => Future.successful(
      request.keys.map { key =>
        val selected = persisted.now().getOrElse(key, empty).cleanWithStrategy(request.strategy)
        key -> selected.events.values.filter(event => !event.timestamp.isBefore(request.starting) && !event.timestamp.isAfter(request.until)).toList
      }.toMap))
    /** Persists the writes and acknowledges each sent event the destination holds afterwards. */
    val writeOperation = State[Map[SyncContext, History] => Future[SyncSuccess[EventId]]](values => {
      val previous = persisted.now()
      persisted.update(current => values.foldLeft(current) { case (merged, (key, history)) =>
        merged.updated(key, merged.getOrElse(key, empty).mergeWith(history))
      })
      Future.successful(values.map { (key, history) =>
        SyncSuccess.immutable(history.events.ids, previous.get(key).fold(Set.empty)(_.events.ids), persisted.now()(key).events.ids)
      }.foldLeft(SyncSuccess.emptyNow[EventId]())(_.combine(_)))
    })
    val reader = new RemoteHistoryDataReader[SyncContext, TextInteractionFormat.type] {
      def fetchEventsBetween(logger: SyncLogger, keys: Seq[SyncContext], starting: LocalDateTime, until: LocalDateTime,
          strategy: EventSyncStrategy): Future[Map[SyncContext, List[Event]]] = {
        val request = Read(keys, starting, until, strategy)
        reads.update(_ :+ request)
        readOperation.now()(request)
      }
    }
    val writer = new RemoteHistoryDataWriter[SyncContext, TextInteractionFormat.type] {
      def writeForKey(logger: SyncLogger, key: SyncContext, dataValue: History): Future[SyncSuccess[EventId]] = writeAll(logger, Map(key -> dataValue))
      def writeAll(logger: SyncLogger, values: Map[SyncContext, History]): Future[SyncSuccess[EventId]] = {
        writes.update(_ :+ values)
        writeOperation.now()(values)
      }
      override def flush(logger: SyncLogger, forceFlush: Boolean): Unit = ()
    }
  }
  private def cache(destinations: (Destination, EventSyncStrategy)*): Cache = RemoteHistoryCache(logger, destinations.toMap)
  private def local(cache: Cache, historyKey: SyncContext = key): History = cache.updateReport.now().get(historyKey)
  private def reportOf(cache: Cache, historyKey: SyncContext = key) = cache.reports.now().get(historyKey)
  private def syncingEverything(destination: Destination): Cache = cache(destination -> EventSyncStrategy.SYNC_EVERYTHING)

  test("local histories merge without IO and reports select pending events per destination") {
    val majorOnly = new Destination
    val all = new Destination
    val control = cache(majorOnly -> EventSyncStrategy.KeepImportance(Set(UpdateImportance.MAJOR)), all -> EventSyncStrategy.SYNC_EVERYTHING)
    val major = event(1, 1)
    val minor = event(2, 2, UpdateImportance.MINOR)
    control.mergeLocal(Map(key -> history(major)))
    control.mergeLocal(Map(key -> history(minor)))
    val report = reportOf(control)
    assertEquals(local(control).events.values, Set(major, minor))
    assertEquals(report.pendingWrites(majorOnly).events.values, Set(major))
    assertEquals(report.pendingWrites(all).events.values, Set(major, minor))
    assertEquals(report.syncedDestinations, Set.empty)
    assertEquals(all.reads.now(), Nil)
    assertEquals(all.writes.now(), Nil)
  }

  test("cold reads fetch full histories and successful empty reads also become fresh") {
    val destination = new Destination
    destination.persisted.set(Map(key -> history(event(1, 1))))
    val control = syncingEverything(destination)
    control.ensureMaxAge(Set(key, otherKey)).flatMap { _ =>
      assertEquals(destination.reads.now().head.starting, LocalDateTime.MIN)
      assertEquals(local(control).events.values, Set(event(1, 1)))
      assertEquals(local(control, otherKey), empty)
      assert(reportOf(control, otherKey).lastKnownValues(destination).fetchCompleted.nonEmpty)
      control.ensureMaxAge(Set(key, otherKey), LocalDateTime.MIN)
    }.map(_ => assertEquals(destination.reads.now().size, 1))
  }

  test("incremental reads start at the last read event timestamp and replay equal-time arrivals") {
    val destination = new Destination
    val first = event(1, 5)
    val tied = event(2, 5)
    val newer = event(3, 6)
    destination.persisted.set(Map(key -> history(first)))
    val control = syncingEverything(destination)
    control.ensureMaxAge(Set(key)).flatMap { _ =>
      destination.persisted.set(Map(key -> history(first, tied, newer)))
      control.ensureMaxAge(Set(key), LocalDateTime.MAX)
    }.map { _ =>
      assertEquals(destination.reads.now().last.starting, time.plusSeconds(5))
      assertEquals(destination.reads.now().last.until, LocalDateTime.MAX)
      assertEquals(local(control).events.values, Set(first, tied, newer))
      assertEquals(reportOf(control).lastKnownValues(destination).fetchedThrough, Some(time.plusSeconds(6)))
    }
  }

  test("explicit range reads use inclusive bounds, isolate keys and do not advance tail-read freshness") {
    val destination = new Destination
    val first = event(1, 1)
    val last = event(2, 2)
    destination.readOperation.set(_ => Future.successful(Map(key -> List(event(0, 0), first, last, event(3, 3)), otherKey -> List(first))))
    val control = syncingEverything(destination)
    control.fetchEventsBetween(Seq(key, key), time.plusSeconds(1), time.plusSeconds(2)).flatMap { fetched =>
      assertEquals(fetched.keySet, Set(key))
      assertEquals(fetched(key).toSet, Set(first, last))
      assertEquals(destination.reads.now().head.keys, Seq(key))
      val confirmed = reportOf(control).lastKnownValues(destination)
      assertEquals(confirmed.fetchedThrough, None)
      assertEquals(confirmed.fetchCompleted, None)
      control.ensureMaxAge(Set(key), LocalDateTime.MIN)
    }.map(_ => assertEquals(destination.reads.now().last.starting, LocalDateTime.MIN))
  }

  test("each destination receives its own strategy for both reads and delta writes") {
    val latest = new Destination
    val all = new Destination
    val initial = history(event(1, 1), event(2, 2), event(3, 3, UpdateImportance.MINOR))
    latest.persisted.set(Map(key -> initial))
    all.persisted.set(Map(key -> initial))
    val control = cache(latest -> EventSyncStrategy.KEEP_LAST_MAJOR, all -> EventSyncStrategy.SYNC_EVERYTHING)
    control.fetchAll(Set(key)).flatMap { _ =>
      assertEquals(latest.reads.now().head.strategy, EventSyncStrategy.KEEP_LAST_MAJOR)
      assertEquals(reportOf(control).lastKnownValues(latest).knownEventIds, ids(event(2, 2)))
      control.requestStoreToAll(Map(key -> history(event(4, 4), event(5, 5, UpdateImportance.MINOR))))
    }.flatMap(_ => control.ensureMaxAge(Set(key))).map { _ =>
      assertEquals(latest.writes.now().last(key).events.values, Set(event(4, 4)))
      assertEquals(all.writes.now().last(key).events.values, Set(event(4, 4), event(5, 5, UpdateImportance.MINOR)))
      assertEquals(reportOf(control).syncedDestinations, Set(all))
    }
  }

  test("acknowledged and read events are not resent, and missing older offline events are still written") {
    val destination = new Destination
    val control = syncingEverything(destination)
    val newer = event(2, 20)
    val read = event(3, 30)
    val older = event(1, 1)
    control.requestStoreToAll(Map(key -> history(newer))).flatMap(_ => control.storeAll()).flatMap { _ =>
      assertEquals(destination.writes.now().size, 1, "an acknowledged event is not sent again")
      destination.persisted.update(_.updated(key, history(newer, read)))
      control.ensureMaxAge(Set(key))
    }.flatMap(_ => control.storeAll()).flatMap { _ =>
      assertEquals(destination.writes.now().size, 1, "events read from the destination are not sent again")
      control.requestStoreToAll(Map(key -> history(older)))
    }.map { _ =>
      assertEquals(destination.writes.now().last(key).events.values, Set(older))
      assertEquals(destination.persisted.now()(key).events.values, Set(older, newer, read))
    }
  }

  test("a committed write acknowledgment makes its sent events known without counting as a read") {
    val destination = new Destination
    val control = syncingEverything(destination)
    val pending = State(Option.empty[Promise[SyncSuccess[EventId]]])
    val persist = destination.writeOperation.now()
    destination.writeOperation.set { values =>
      val acknowledged = Promise[SyncSuccess[EventId]]()
      pending.set(Some(acknowledged))
      acknowledged.future.flatMap(_ => persist(values))
    }
    val writing = control.requestStoreToAll(Map(key -> history(event(100, 100))))
    assertEquals(reportOf(control).lastKnownValues, Map.empty, "a requested write is not evidence that it was stored")
    assertEquals(reportOf(control).syncedDestinations, Set.empty)
    pending.now().foreach(_.success(SyncSuccess.emptyNow()))
    writing.map { _ =>
      val known = reportOf(control).lastKnownValues(destination)
      assertEquals(known.knownEventIds, ids(event(100, 100)))
      assertEquals((known.fetchCompleted, known.fetchedThrough, known.fullFetchCompleted), (None, None, None))
      assertEquals(reportOf(control).syncedDestinations, Set(destination))
      assertEquals(destination.reads.now(), Nil)
    }
  }

  test("an acknowledgment confirms only the sent events it names") {
    val destination = new Destination
    val control = syncingEverything(destination)
    val stored = event(1, 1)
    val dropped = event(2, 2)
    destination.writeOperation.set(_ => Future.successful(
      SyncSuccess.emptyNow[EventId]().copy(elementsAdded = ids(stored, event(9, 9)))))
    control.requestStoreToAll(Map(key -> history(stored, dropped))).map { _ =>
      assertEquals(reportOf(control).lastKnownValues(destination).knownEventIds, ids(stored))
      assertEquals(reportOf(control).pendingWrites(destination).events.values, Set(dropped))
    }
  }

  test("received events whose IDs name different local events are discarded, the others are merged") {
    val destination = new Destination
    val control = syncingEverything(destination)
    val mine = event(1, 1)
    val corrupt = AnswerChangedEvent[TextInteractionFormat.type]("changed", mine.timestamp, UpdateImportance.MAJOR, mine.eventId)
    control.mergeLocal(Map(key -> history(mine)))
    destination.readOperation.set(_ => Future.successful(Map(key -> List(corrupt, event(2, 2)))))
    control.ensureMaxAge(Set(key)).map { _ =>
      assertEquals(local(control).events.values, Set(mine, event(2, 2)))
      assertEquals(reportOf(control).lastKnownValues(destination).knownEventIds, ids(event(2, 2)))
    }
  }

  test("failed and synchronously throwing reads retain their cursor and can be retried") {
    val destination = new Destination
    val control = syncingEverything(destination)
    val failure = IllegalStateException("offline")
    destination.readOperation.set(_ => throw failure)
    control.ensureMaxAge(Set(key)).failed.flatMap { error =>
      assert(error eq failure)
      assertEquals(control.reports.now(), Some(Map.empty))
      destination.readOperation.set(_ => Future.failed(failure))
      control.ensureMaxAge(Set(key)).failed
    }.flatMap { error =>
      assert(error eq failure)
      destination.readOperation.set(_ => Future.successful(Map(key -> List(event(1, 1)))))
      control.ensureMaxAge(Set(key))
    }.map(_ => assert(destination.reads.now().forall(_.starting == LocalDateTime.MIN)))
  }

  test("failed and lost writes remain pending and are sent again") {
    val destination = new Destination
    val control = syncingEverything(destination)
    val failure = IllegalStateException("offline")
    val persist = destination.writeOperation.now()
    destination.writeOperation.set(_ => throw failure)
    control.requestStoreToAll(Map(key -> history(event(1, 1)))).failed.flatMap { error =>
      assert(error eq failure)
      assertEquals(reportOf(control).syncedDestinations, Set.empty)
      assertEquals(reportOf(control).lastKnownValues, Map.empty)
      // The destination acknowledges, but names no stored event: the next read finds nothing.
      destination.writeOperation.set(_ => Future.successful(SyncSuccess.emptyNow()))
      control.storeAll().flatMap(_ => control.ensureMaxAge(Set(key), LocalDateTime.MAX))
    }.flatMap { _ =>
      assertEquals(reportOf(control).syncedDestinations, Set.empty)
      assertEquals(reportOf(control).pendingWrites(destination).events.values, Set(event(1, 1)))
      destination.writeOperation.set(persist)
      // Force confirmation even if both reads complete within one clock tick (Scala.js).
      control.storeAll().flatMap(_ => control.ensureMaxAge(Set(key), LocalDateTime.MAX))
    }.map { _ =>
      assertEquals(destination.writes.now().size, 3)
      assertEquals(destination.reads.now().size, 2)
      assertEquals(reportOf(control).syncedDestinations, Set(destination))
    }
  }

  test("delayed reads merge with newer local edits instead of overwriting them") {
    val destination = new Destination
    val gate = Promise[Map[SyncContext, List[Event]]]()
    destination.readOperation.set(_ => gate.future)
    val control = syncingEverything(destination)
    val reading = control.ensureMaxAge(Set(key))
    control.requestStoreToAll(Map(key -> history(event(2, 2)))).flatMap { _ =>
      gate.success(Map(key -> List(event(1, 1))))
      reading
    }.map { _ =>
      assertEquals(local(control).events.values, Set(event(1, 1), event(2, 2)))
      val confirmed = reportOf(control).lastKnownValues(destination)
      assertEquals(confirmed.knownEventIds, ids(event(1, 1), event(2, 2)), "read and acknowledged events are known")
      assertEquals(confirmed.fetchedThrough, Some(time.plusSeconds(1)), "only reads advance the cursor")
      assertEquals(reportOf(control).pendingWrites(destination).events.values, Set.empty[Event])
    }
  }

  test("overlapping reads may finish in reverse order without losing events or moving the cursor backwards") {
    val destination = new Destination
    val first = Promise[Map[SyncContext, List[Event]]]()
    val second = Promise[Map[SyncContext, List[Event]]]()
    destination.readOperation.set(_ => first.future)
    val control = syncingEverything(destination)
    val olderRead = control.ensureMaxAge(Set(key))
    destination.readOperation.set(_ => second.future)
    val newerRead = control.ensureMaxAge(Set(key))
    second.success(Map(key -> List(event(2, 2))))
    newerRead.flatMap { _ =>
      first.success(Map(key -> List(event(1, 1))))
      olderRead
    }.map { _ =>
      assertEquals(local(control).events.values, Set(event(1, 1), event(2, 2)))
      assertEquals(reportOf(control).lastKnownValues(destination).fetchedThrough, Some(time.plusSeconds(2)))
    }
  }

  test("an acknowledged write confirms the events it sent and does not hide newer local events") {
    val destination = new Destination
    val gate = Promise[Unit]()
    val persist = destination.writeOperation.now()
    destination.writeOperation.set(values => gate.future.flatMap(_ => persist(values)))
    val control = syncingEverything(destination)
    val writing = control.requestStoreToAll(Map(key -> history(event(1, 1))))
    control.mergeLocal(Map(key -> history(event(2, 2))))
    gate.success(())
    writing.flatMap { _ =>
      val report = reportOf(control)
      assertEquals(report.lastKnownValues(destination).knownEventIds, ids(event(1, 1)))
      assertEquals(report.pendingWrites(destination).events.values, Set(event(2, 2)))
      control.storeAll()
    }.map(_ => assertEquals(destination.writes.now().last(key).events.values, Set(event(2, 2))))
  }

  test("buffered writers receive their requests before the cache flushes them") {
    val queued = State(Option.empty[(Map[SyncContext, History], Promise[SyncSuccess[EventId]])])
    val destination = new Destination {
      override val writer = new RemoteHistoryDataWriter[SyncContext, TextInteractionFormat.type] {
        def writeForKey(logger: SyncLogger, key: SyncContext, value: History) = writeAll(logger, Map(key -> value))
        def writeAll(logger: SyncLogger, values: Map[SyncContext, History]) = {
          val promise = Promise[SyncSuccess[EventId]]()
          writes.update(_ :+ values)
          queued.set(Some((values, promise)))
          promise.future
        }
        override def flush(logger: SyncLogger, forceFlush: Boolean): Unit = if forceFlush then {
          val pending = queued.now()
          queued.set(None)
          pending.foreach((values, promise) => promise.completeWith(writeOperation.now()(values)))
        }
      }
    }
    val control = syncingEverything(destination)
    val first = control.requestStoreToAll(Map(key -> history(event(1, 1))))
    assert(!first.isCompleted)
    control.flush(true)
    first.flatMap(_ => control.requestStoreToAll(Map(key -> history(event(2, 2))), forceFlush = true)).flatMap { _ =>
      assertEquals(queued.now(), None)
      control.ensureMaxAge(Set(key))
    }.map(_ => assertEquals(reportOf(control).syncedDestinations, Set(destination)))
  }

  test("grading and scaffolding timestamps advance the read cursor and synchronize as independent deltas") {
    import it.evadid.workbook.abstractions.FeedbackEntity
    import it.evadid.workbook.abstractions.grading.*
    import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
    import it.evadid.core.datastructures.language.LanguageMap
    val grader = new WorkbookInteractionGrader[String, Unit] {
      override val key = "cache-test-grader"
      override val importance = GradingImportance.REQUIRED_TEST
      override val runningBehavior = GraderRunningBehavior.RUN_ALWAYS
      override val gradingEntity = FeedbackEntity.TestEntity
      override protected def handleGrading(state: String, context: it.evadid.workbook.meta.ExecutionContextInformation)
          : Future[GradingResult[String, Unit]] = throw AssertionError("The cache must not execute graders")
    }
    val grading: Event = GradingCompletedEvent(GradingResult(grader, "1", GradingStatus.Correct, None, None, time.plusSeconds(5)))
    val scaffolding: Event = ScaffoldingCompletedEvent(ScaffoldingResult("1",
      ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.universalMap("Hint")), FeedbackEntity.LlmEntity, None, time.plusSeconds(6)))
    val destination = new Destination
    destination.persisted.set(Map(key -> history(event(1, 1), grading, scaffolding)))
    val control = syncingEverything(destination)
    control.ensureMaxAge(Set(key)).flatMap { _ =>
      assertEquals(reportOf(control).lastKnownValues(destination).fetchedThrough, Some(time.plusSeconds(6)))
      control.requestStoreToAll(Map(key -> history(ScaffoldingStartedEvent[TextInteractionFormat.type]("1", time.plusSeconds(7), FeedbackEntity.LlmEntity))))
    }.map { _ =>
      val written = destination.writes.now().last(key).events.values
      assertEquals(written.size, 1)
      assert(written.head.isInstanceOf[ScaffoldingStartedEvent[?]])
      assertEquals(local(control).events.values.size, 4)
    }
  }

  test("fetchAll retrieves an older late arrival that is outside the incremental timestamp range") {
    val destination = new Destination
    val control = syncingEverything(destination)
    destination.persisted.set(Map(key -> history(event(10, 10))))
    control.fetchAll(Set(key)).flatMap { _ =>
      destination.persisted.set(Map(key -> history(event(1, 1), event(10, 10))))
      control.ensureMaxAge(Set(key), LocalDateTime.MAX)
    }.flatMap { _ =>
      assertEquals(local(control).events.values, Set(event(10, 10)))
      control.fetchAll(Set(key))
    }.map { events =>
      assertEquals(events(key).toSet, Set(event(1, 1), event(10, 10)))
      assertEquals(local(control).events.values, Set(event(1, 1), event(10, 10)))
    }
  }

  test("empty requests do no IO; invalid ranges and unconfigured destinations fail explicitly") {
    val destination = new Destination
    val control = syncingEverything(destination)
    for {
      _ <- control.ensureMaxAge(Set.empty)
      _ <- control.fetchAll(Set.empty)
      _ <- control.fetchEventsBetween(Nil, time, time)
      _ <- control.storeAll()
      _ = assertEquals(destination.reads.now(), Nil)
      _ = assertEquals(destination.writes.now(), Nil)
      invalid <- control.fetchEventsBetween(Seq(key), time.plusSeconds(1), time).failed
      _ = assert(invalid.isInstanceOf[IllegalArgumentException])
      unknown <- control.requestStore(new Destination, Map(key -> empty)).failed
    } yield assert(unknown.isInstanceOf[IllegalArgumentException])
  }
  test("destination reads restore successful events independently of an unavailable peer") {
    val available = new Destination
    val unavailable = new Destination
    val saved = event(1, 1)
    available.persisted.set(Map(key -> history(saved)))
    unavailable.readOperation.set(_ => Future.failed(IllegalStateException("offline")))
    val control = cache(available -> EventSyncStrategy.SYNC_EVERYTHING, unavailable -> EventSyncStrategy.SYNC_EVERYTHING)
    val good = control.fetchAllFrom(available, Set(key))
    val bad = control.fetchAllFrom(unavailable, Set(key))
    good.zip(bad.failed).map { (received, error) =>
      assertEquals(received(key).toSet, Set(saved))
      assertEquals(local(control).events.values, Set(saved))
      assertEquals(error.getMessage, "offline")
      assert(!reportOf(control).lastKnownValues.contains(unavailable))
    }
  }
  test("sync confirmation tracks the latest answer and associated lifecycle events, not all historical writes") {
    import it.evadid.workbook.abstractions.FeedbackEntity.LlmEntity
    val destination = new Destination
    val control = cache(destination -> EventSyncStrategy.SYNC_MAJOR)
    val old = event(1, 1, UpdateImportance.MINOR)
    val current = event(2, 2, UpdateImportance.MINOR)
    val pending: Event = ScaffoldingStartedEvent[TextInteractionFormat.type]("2", time.plusSeconds(3), LlmEntity)
    val initial = history(old, current, pending)
    control.requestStoreToAll(Map(key -> initial)).flatMap(_ => control.ensureMaxAge(Set(key))).flatMap { _ =>
      assertEquals(destination.persisted.now()(key).events.values, Set(current, pending))
      assertEquals(reportOf(control).syncedDestinations, Set(destination))
      val newPending: Event = ScaffoldingStartedEvent[TextInteractionFormat.type]("2", time.plusSeconds(4), LlmEntity)
      control.mergeLocal(Map(key -> history(newPending)))
      assertEquals(reportOf(control).syncedDestinations, Set.empty)
      control.storeAll()
    }.flatMap { _ =>
      assertEquals(reportOf(control).syncedDestinations, Set(destination), "the acknowledgment confirms the new event")
      assertEquals(destination.writes.now().last(key).size, 1)
      control.storeAll()
    }.map(_ => assertEquals(destination.writes.now().size, 2))
  }

  test("a newer page default never replaces the restored answer for selection or sync confirmation") {
    val destination = new Destination
    val control = cache(destination -> EventSyncStrategy.SYNC_MAJOR)
    val restored = event(1, 1)
    val pageDefault = event(0, 10, UpdateImportance.DEFAULT)
    control.requestStoreToAll(Map(key -> history(restored, pageDefault))).flatMap(_ => control.ensureMaxAge(Set(key))).map { _ =>
      assertEquals(destination.persisted.now()(key).events.values, Set(restored))
      assertEquals(reportOf(control).syncedDestinations, Set(destination))
    }
  }

  test("range reads retain selected pending feedback even when its answer lies outside the window") {
    import it.evadid.workbook.abstractions.FeedbackEntity.LlmEntity
    val destination = new Destination
    val control = cache(destination -> EventSyncStrategy.SYNC_MAJOR)
    val answer = event(1, 1)
    val pending: Event = ScaffoldingStartedEvent[TextInteractionFormat.type]("1", time.plusSeconds(2), LlmEntity)
    destination.persisted.set(Map(key -> history(answer, pending)))
    control.fetchEventsBetween(Seq(key), time.plusSeconds(2), time.plusSeconds(2)).map { received =>
      assertEquals(received(key).toSet, Set(pending))
      assertEquals(local(control).events.values, Set(pending))
    }
  }

  test("normal destination refreshes use inclusive cursors and scheduled full reads recover older offline events") {
    val destination = new Destination
    val control = syncingEverything(destination)
    val first = event(1, 10)
    val late = event(2, 1)
    destination.persisted.set(Map(key -> history(first)))
    control.refreshFrom(destination, Set(key), LocalDateTime.MIN).flatMap { _ =>
      assertEquals(destination.reads.now().last.starting, LocalDateTime.MIN)
      destination.persisted.set(Map(key -> history(first, late)))
      control.refreshFrom(destination, Set(key), LocalDateTime.MIN)
    }.flatMap { _ =>
      assertEquals(destination.reads.now().last.starting, first.timestamp)
      assertEquals(local(control).events.values, Set(first))
      control.refreshFrom(destination, Set(key), LocalDateTime.MAX)
    }.map { _ =>
      assertEquals(destination.reads.now().last.starting, LocalDateTime.MIN)
      assertEquals(local(control).events.values, Set(first, late))
    }
  }

}
