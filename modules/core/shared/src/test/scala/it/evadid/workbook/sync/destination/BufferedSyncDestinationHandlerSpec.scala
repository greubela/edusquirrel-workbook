package it.evadid.workbook.sync.destination


import it.evadid.core.datastructures.state.State
import it.evadid.util.logging.BasicLogger
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.sync.*
import it.evadid.workbook.sync.model.{FetchResponse, InteractionHistoryJson}
import it.evadid.workbook.history.InteractionHistory
import it.evadid.workbook.history.events.{AnswerChangedEvent, EventId, InteractionEvent, InteractionEvents}
import munit.FunSuite
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future, Promise}

class BufferedSyncDestinationHandlerSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val context = UsageContext("program", "workbook", "user")
  private val key = context.toSyncContext("answer")
  private def saved(value: String): String = {
    val format = InteractionFormat.TextInteractionFormat
    val history = InteractionHistory.empty(format)
      .addEvent(AnswerChangedEvent[format.type](value, java.time.LocalDateTime.of(2026, 1, 1, 12, 0).toInstant(java.time.ZoneOffset.UTC), UpdateImportance.MAJOR, eventId(value)))
    InteractionHistoryJson.fromHistory(format, history).serialized
  }
  private def eventId(value: String): EventId = EventId(new java.util.UUID(0L, value.hashCode.toLong).toString)
  private def merged(values: String*): String = InteractionHistoryJson.mergeSerialized(values.map(saved))
  /** Makes the destination's stores wait on the returned gate; the first promise completes once a store has started. */
  private def gateStores(destination: Destination): (Promise[Unit], Promise[SyncSuccess[EventId]]) = {
    val started = Promise[Unit]()
    val gate = Promise[SyncSuccess[EventId]]()
    destination.storeResult.set(() => { started.trySuccess(()); gate.future })
    (started, gate)
  }
  /** One write and one read fail with `error`; after `recover`, a retried write reaches the destination. */
  private def assertBatchFailsThenRetries(destination: Destination, error: Throwable)(recover: => Unit): Future[Unit] = {
    val buffered = destination.toBuffered
    val writing = buffered.storeHistoryJson(key, saved("first"))
    val reading = buffered.readHistoryJson(key)
    buffered.flush(true)
    for {
      writeError <- writing.failed
      readError <- reading.failed
      _ = assert(writeError eq error)
      _ = assert(readError eq error)
      _ = recover
      retried = buffered.storeHistoryJson(key, saved("retry"))
      _ = buffered.flush(true)
      _ <- retried
    } yield assertEquals(destination.writes.now().last, Map(key -> saved("retry")))
  }
  private class Destination extends SyncDestinationHistoryHandler {
    val syncLogger = SyncLogger(BasicLogger())
    val definition = SyncDestinationDefinition.defaultBrowserHistory
    val writes = State(List.empty[Map[SyncContext, String]])
    val reads = State(List.empty[UsageContext])
    val storeResult = State[() => Future[SyncSuccess[EventId]]](() => Future.successful(SyncSuccess.emptyNow()))
    val readResult = State[UsageContext => Future[FetchResponse[SyncContext, String]]](ctx =>
      Future.successful(FetchResponse.fromMap(Instant.now(), Map(ctx.toSyncContext("answer") -> ctx.userId))))
    def storeHistoryJson(key: SyncContext, json: String)(implicit ec: ExecutionContext) = storeHistoriesJson(Map(key -> json))
    override def storeHistoriesJson(values: Map[SyncContext, String])(implicit ec: ExecutionContext) = {
      writes.update(_ :+ values)
      storeResult.now()()
    }
    def readHistoriesJson(ctx: UsageContext)(implicit ec: ExecutionContext) = { reads.update(_ :+ ctx); readResult.now()(ctx) }
    def clearAllHistories(ctx: UsageContext)(using ExecutionContext) = clearValues(ctx)
    def clearValue(key: SyncContext)(implicit ec: ExecutionContext) = Future.successful(SyncSuccess.emptyNow[SyncContext]())
    def clearValues(ctx: UsageContext)(implicit ec: ExecutionContext) = Future.successful(SyncSuccess.emptyNow[SyncContext]())
  }

  test("one flush batches all keys, merges queued event histories and completes every caller") {
    val destination = new Destination
    val buffered = destination.toBuffered
    val first = buffered.storeHistoryJson(key, saved("old"))
    val second = buffered.storeHistoriesJson(Map(key -> saved("new"), context.toSyncContext("other") -> saved("other")))
    assertEquals(destination.writes.now(), Nil)
    buffered.flush(true)
    first.flatMap(_ => second).map { _ =>
      assertEquals(destination.writes.now(), List(Map(key -> merged("old", "new"), context.toSyncContext("other") -> saved("other"))))
    }
  }
  test("every caller of a merged batch receives the confirmations of its own events only") {
    val destination = new Destination
    val other = context.toSyncContext("other")
    destination.storeResult.set(() => Future.successful(SyncSuccess.emptyNow[EventId]().copy(
      elementsAdded = Set(eventId("old"), eventId("other")), elementsUnchanged = Set(eventId("new")))))
    val buffered = destination.toBuffered
    val first = buffered.storeHistoryJson(key, saved("old"))
    val second = buffered.storeHistoriesJson(Map(key -> saved("new"), other -> saved("other")))
    val third = buffered.storeHistoryJson(other, saved("unconfirmed"))
    buffered.flush(true)
    for { a <- first; b <- second; c <- third } yield {
      assertEquals(destination.writes.now().size, 1)
      assertEquals(a.elementsStored, Set(eventId("old")))
      assertEquals((b.elementsAdded, b.elementsUnchanged), (Set(eventId("other")), Set(eventId("new"))))
      assertEquals(c.elementsStored, Set.empty[EventId])
    }
  }
  test("reads share one fetch per usage context and do not mix users") {
    val destination = new Destination
    val buffered = destination.toBuffered
    val first = buffered.readHistoryJson(key)
    val repeated = buffered.readHistoriesJson(context)
    val otherContext = context.copy(userId = "other")
    val other = buffered.readHistoriesJson(otherContext)
    buffered.flush(true)
    for {
      a <- first
      b <- repeated
      c <- other
    } yield {
      assertEquals(a.asMap.keySet, Set(key))
      assertEquals(a, b)
      assertEquals(c.asMap.keySet, Set(otherContext.toSyncContext("answer")))
      assertEquals(destination.reads.now().toSet, Set(context, otherContext))
      assertEquals(destination.reads.now().size, 2)
    }
  }
  test("a failed context read does not fail other users in the batch") {
    val destination = new Destination
    val buffered = destination.toBuffered
    val failure = IllegalStateException("unreachable")
    destination.readResult.set(ctx => if ctx == context then Future.failed(failure)
      else Future.successful(FetchResponse.fromMap(Instant.now(), Map(ctx.toSyncContext("answer") -> "ok"))))
    val failed = buffered.readHistoriesJson(context)
    val good = buffered.readHistoriesJson(context.copy(userId = "other"))
    buffered.flush(true)
    failed.failed.flatMap { error =>
      assert(error eq failure)
      good.map(result => assertEquals(result.fetchedValues.head.dataValue, "ok"))
    }
  }
  test("flushed write batches stay ordered and a failed batch does not block retries") {
    val destination = new Destination
    val buffered = destination.toBuffered
    val (started, gate) = gateStores(destination)
    val first = buffered.storeHistoryJson(key, saved("first"))
    buffered.flush(true)
    started.future.flatMap { _ =>
      destination.storeResult.set(() => Future.successful(SyncSuccess.emptyNow()))
      val second = buffered.storeHistoryJson(key, saved("second"))
      buffered.flush(true)
      assertEquals(destination.writes.now(), List(Map(key -> saved("first"))))
      gate.failure(IllegalStateException("first failed"))
      first.failed.flatMap(_ => second).map(_ => assertEquals(destination.writes.now(), List(Map(key -> saved("first")), Map(key -> saved("second")))))
    }
  }
  test("the reader/writer adapter registers bulk requests before flushing") {
    val destination = new Destination
    val bound = SyncInformation(destination.definition, EventSyncStrategy.SYNC_EVERYTHING).forContext(context, _ => destination.toBuffered)
    val format = InteractionFormat.TextInteractionFormat
    val typed = FormattedSyncDestination(bound, format)
    def history(value: String) = InteractionHistoryJson.deserialize(saved(value)).typedAs(format)
    val writing = typed.writer.writeAll(bound.handler.syncLogger, Map(key -> history("one"), context.toSyncContext("other") -> history("two")))
    typed.writer.flush(bound.handler.syncLogger, true)
    writing.map(_ => assertEquals(destination.writes.now().head.size, 2))
  }
  test("the request count threshold flushes automatically without an explicit flush") {
    val destination = new Destination
    val buffered = destination.toBuffered
    val pending = (1 to 30).map(index => buffered.storeHistoryJson(key, saved(index.toString)))
    Future.sequence(pending).map { _ =>
      assertEquals(destination.writes.now(), List(Map(key -> InteractionHistoryJson.mergeSerialized((1 to 30).map(index => saved(index.toString))))))
      assertEquals(buffered.timestamps, Set.empty)
    }
  }

  test("reads in a batch wait for its writes and observe the committed values") {
    val committed = State(Map.empty[SyncContext, String])
    val destination = new Destination {
      override def storeHistoriesJson(values: Map[SyncContext, String])(implicit ec: ExecutionContext) =
        super.storeHistoriesJson(values).map { success => committed.update(_ ++ values); success }
      override def readHistoriesJson(ctx: UsageContext)(implicit ec: ExecutionContext) = {
        reads.update(_ :+ ctx)
        Future.successful(FetchResponse.fromMap(Instant.now(), committed.now()))
      }
    }
    val (started, gate) = gateStores(destination)
    val buffered = destination.toBuffered
    val writing = buffered.storeHistoryJson(key, saved("saved"))
    val reading = buffered.readHistoryJson(key)
    buffered.flush(true)
    started.future.flatMap { _ =>
      assertEquals(destination.reads.now(), Nil)
      assert(!reading.isCompleted)
      gate.success(SyncSuccess.emptyNow())
      writing.flatMap(_ => reading).map(result => assertEquals(result.asMap(key), saved("saved")))
    }
  }

  test("synchronous handler exceptions fail their callers and do not block the next batch") {
    val destination = new Destination
    val error = IllegalStateException("synchronous failure")
    destination.storeResult.set(() => throw error)
    destination.readResult.set(_ => throw error)
    assertBatchFailsThenRetries(destination, error)(destination.storeResult.set(() => Future.successful(SyncSuccess.emptyNow())))
  }

  test("a clear runs after pending writes and before writes submitted afterwards") {
    val operations = State(List.empty[String])
    val persisted = State(Map.empty[SyncContext, String])
    val destination = new Destination {
      override def storeHistoriesJson(values: Map[SyncContext, String])(implicit ec: ExecutionContext) = {
        operations.update(_ :+ s"write:${values(key)}")
        super.storeHistoriesJson(values).map { success => persisted.update(_ ++ values); success }
      }
      override def clearValues(ctx: UsageContext)(implicit ec: ExecutionContext) = {
        operations.update(_ :+ "clear")
        persisted.set(Map.empty)
        Future.successful(SyncSuccess.emptyNow[SyncContext]())
      }
    }
    val (started, gate) = gateStores(destination)
    val buffered = destination.toBuffered
    val old = buffered.storeHistoryJson(key, saved("old"))
    buffered.flush(true)
    started.future.flatMap { _ =>
      val clearing = buffered.clearValues(context)
      destination.storeResult.set(() => Future.successful(SyncSuccess.emptyNow()))
      val fresh = buffered.storeHistoryJson(key, saved("new"))
      buffered.flush(true)
      assertEquals(operations.now(), List(s"write:${saved("old")}"))
      gate.success(SyncSuccess.emptyNow())
      for { _ <- old; _ <- clearing; _ <- fresh } yield {
        assertEquals(operations.now(), List(s"write:${saved("old")}", "clear", s"write:${saved("new")}"))
        assertEquals(persisted.now(), Map(key -> saved("new")))
      }
    }
  }

  test("a delegated buffer is flushed after requests have actually been registered") {
    val destination = new Destination
    val inner = destination.toBuffered
    val buffered = BufferedSyncDestinationHandler(inner)
    val writing = buffered.storeHistoryJson(key, saved("saved"))
    val reading = buffered.readHistoryJson(key)
    buffered.flush(true)
    writing.flatMap(_ => reading).map { response =>
      assertEquals(destination.writes.now(), List(Map(key -> saved("saved"))))
      assertEquals(response.asMap.keySet, Set(key))
      assertEquals(inner.timestamps, Set.empty)
    }
  }

  test("a delegate flush failure reaches waiting callers and the next batch can retry") {
    val failing = State(true)
    val error = IllegalStateException("flush failed")
    val destination = new Destination {
      override def flush(forceFlush: Boolean): Unit = if failing.now() then throw error
    }
    assertBatchFailsThenRetries(destination, error)(failing.set(false))
  }
  test("range requests batch matching windows and preserve keys, strategy and nanosecond bounds") {
    val calls = State(Vector.empty[(Seq[SyncContext], Instant, Instant, EventSyncStrategy)])
    val from = java.time.LocalDateTime.of(2026, 1, 1, 12, 0).toInstant(java.time.ZoneOffset.UTC).plusNanos(1)
    val until = from.plusNanos(2)
    val other = context.toSyncContext("other")
    val destination = new Destination {
      override def readHistoriesInRange(ctx: UsageContext, keys: Seq[SyncContext], start: Instant,
          end: Instant, strategy: EventSyncStrategy)(using ExecutionContext) = {
        calls.update(_ :+ (keys, start, end, strategy))
        Future.successful(keys.map(key => key -> InteractionHistoryJson.deserialize(saved(key.keyForSerialisation))).toMap)
      }
    }
    val buffered = destination.toBuffered
    val first = buffered.readHistoriesInRange(context, Vector(key), from, until, EventSyncStrategy.SYNC_MAJOR)
    val second = buffered.readHistoriesInRange(context, Vector(other), from, until, EventSyncStrategy.SYNC_MAJOR)
    buffered.flush(true)
    for { a <- first; b <- second } yield {
      assertEquals(a.keySet, Set(key))
      assertEquals(b.keySet, Set(other))
      assertEquals(calls.now().size, 1)
      assertEquals(calls.now().head._1.toSet, Set(key, other))
      assertEquals(calls.now().head._2, from)
      assertEquals(calls.now().head._3, until)
      assertEquals(calls.now().head._4, EventSyncStrategy.SYNC_MAJOR)
      assertEquals(destination.reads.now(), Nil)
    }
  }

  test("a malformed history is rejected on submission without failing the other queued writes") {
    val destination = new Destination
    val buffered = destination.toBuffered
    val valid = buffered.storeHistoryJson(key, saved("valid"))
    val invalid = buffered.storeHistoryJson(context.toSyncContext("invalid"), "malformed")
    assert(invalid.isCompleted, "the request never joins the batch")
    buffered.flush(true)
    for {
      _ <- invalid.failed
      _ <- valid
    } yield assertEquals(destination.writes.now(), List(Map(key -> saved("valid"))))
  }

  test("different cursor windows share one query and each caller receives only its own events") {
    val calls = State(Vector.empty[(Instant, Instant)])
    val base = java.time.LocalDateTime.of(2026, 1, 1, 12, 0).toInstant(java.time.ZoneOffset.UTC)
    val other = context.toSyncContext("other")
    val format = InteractionFormat.TextInteractionFormat
    val events = (0 to 3).map(index => AnswerChangedEvent[format.type](
      index.toString, base.plusNanos(index), UpdateImportance.MAJOR)).toSet
    val full = InteractionHistoryJson.fromHistory(format,
      InteractionHistory(format, InteractionEvents(events.map(event => event: InteractionEvent[format.type]))))
    val destination = new Destination {
      override def readHistoriesInRange(ctx: UsageContext, keys: Seq[SyncContext], from: Instant,
          until: Instant, strategy: EventSyncStrategy)(using ExecutionContext) = {
        calls.update(_ :+ (from -> until))
        Future.successful(keys.map(_ -> full).toMap)
      }
    }
    val buffer = destination.toBuffered
    val first = buffer.readHistoriesInRange(context, Seq(key), base.plusNanos(1), base.plusNanos(1), EventSyncStrategy.SYNC_EVERYTHING)
    val second = buffer.readHistoriesInRange(context, Seq(other), base.plusNanos(2), base.plusNanos(3), EventSyncStrategy.SYNC_EVERYTHING)
    buffer.flush(true)
    for { a <- first; b <- second } yield {
      assertEquals(calls.now(), Vector(base.plusNanos(1) -> base.plusNanos(3)))
      assertEquals(a.keySet, Set(key))
      assertEquals(b.keySet, Set(other))
      assertEquals(a(key).typedAs(format).answerEvents.map(_.newState), Set("1"))
      assertEquals(b(other).typedAs(format).answerEvents.map(_.newState), Set("2", "3"))
    }
  }


  test("a range read over raw stored values discards an unreadable record and returns the others") {
    val destination = new Destination
    val broken = context.toSyncContext("broken")
    destination.readResult.set(ctx => Future.successful(FetchResponse.fromMap(Instant.now(),
      Map(key -> saved("kept"), broken -> "{\"not\": \"a history\"}"))))
    val from = java.time.LocalDateTime.of(2026, 1, 1, 0, 0).toInstant(java.time.ZoneOffset.UTC)
    destination.readHistoriesInRange(context, Seq(key, broken), from, from.plus(java.time.Duration.ofDays(1)), EventSyncStrategy.SYNC_EVERYTHING).map { read =>
      assertEquals(read.keySet, Set(key))
      assertEquals(read(key).typedAs(InteractionFormat.TextInteractionFormat).answerEvents.map(_.newState), Set("kept"))
    }
  }

  test("an unreadable record in a shared range answer is left out for every waiting caller instead of stalling them") {
    val other = context.toSyncContext("other")
    val destination = new Destination {
      override def readHistoriesInRange(ctx: UsageContext, keys: Seq[SyncContext], from: Instant,
          until: Instant, strategy: EventSyncStrategy)(using ExecutionContext) =
        Future.successful(Map(key -> InteractionHistoryJson.deserialize(saved("kept")),
          other -> InteractionHistoryJson(InteractionFormat.TextInteractionFormat, "{\"not\": \"a history\"}")))
    }
    val buffer = destination.toBuffered
    val from = java.time.LocalDateTime.of(2026, 1, 1, 0, 0).toInstant(java.time.ZoneOffset.UTC)
    val broken = buffer.readHistoriesInRange(context, Seq(other), from, from.plus(java.time.Duration.ofDays(1)), EventSyncStrategy.SYNC_EVERYTHING)
    val kept = buffer.readHistoriesInRange(context, Seq(key), from, from.plus(java.time.Duration.ofDays(1)), EventSyncStrategy.SYNC_EVERYTHING)
    buffer.flush(true)
    for { a <- broken; b <- kept } yield {
      assertEquals(a, Map.empty)
      assertEquals(b.keySet, Set(key))
    }
  }

}
