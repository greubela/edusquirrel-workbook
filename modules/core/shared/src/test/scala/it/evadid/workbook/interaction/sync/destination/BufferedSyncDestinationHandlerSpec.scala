package it.evadid.workbook.interaction.sync.destination

import it.evadid.core.datastructures.state.State
import it.evadid.util.logging.BasicLogger
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.model.FetchResponse
import munit.FunSuite
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future, Promise}

class BufferedSyncDestinationHandlerSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val context = UsageContext("program", "workbook", "user")
  private val key = context.toSyncContext("answer")
  private class Destination extends SyncDestinationHistoryHandler {
    val syncLogger = SyncLogger(BasicLogger())
    val definition = SyncDestinationDefinition.defaultBrowserHistory
    val writes = State(List.empty[Map[SyncContext, String]])
    val reads = State(List.empty[UsageContext])
    val storeResult = State[() => Future[SyncSuccess]](() => Future.successful(SyncSuccess.emptyNow()))
    val readResult = State[UsageContext => Future[FetchResponse[SyncContext, String]]](ctx =>
      Future.successful(FetchResponse.fromMap(LocalDateTime.now(), Map(ctx.toSyncContext("answer") -> ctx.userId))))
    def storeHistoryJson(key: SyncContext, json: String)(implicit ec: ExecutionContext) = storeHistoriesJson(Map(key -> json))
    override def storeHistoriesJson(values: Map[SyncContext, String])(implicit ec: ExecutionContext) = {
      writes.update(_ :+ values)
      storeResult.now()()
    }
    def readHistoriesJson(ctx: UsageContext)(implicit ec: ExecutionContext) = { reads.update(_ :+ ctx); readResult.now()(ctx) }
    def clearValue(key: SyncContext)(implicit ec: ExecutionContext) = Future.successful(SyncSuccess.emptyNow())
    def clearValues(ctx: UsageContext)(implicit ec: ExecutionContext) = Future.successful(SyncSuccess.emptyNow())
  }

  test("one flush batches all keys, takes the latest queued snapshot and completes every caller") {
    val destination = new Destination
    val buffered = destination.toBuffered
    val first = buffered.storeHistoryJson(key, "old")
    val second = buffered.storeHistoriesJson(Map(key -> "new", context.toSyncContext("other") -> "other"))
    assertEquals(destination.writes.now(), Nil)
    buffered.flush(true)
    first.flatMap(_ => second).map { _ =>
      assertEquals(destination.writes.now(), List(Map(key -> "new", context.toSyncContext("other") -> "other")))
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
      else Future.successful(FetchResponse.fromMap(LocalDateTime.now(), Map(ctx.toSyncContext("answer") -> "ok"))))
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
    val started = Promise[Unit]()
    val gate = Promise[SyncSuccess]()
    destination.storeResult.set(() => { started.trySuccess(()); gate.future })
    val first = buffered.storeHistoryJson(key, "first")
    buffered.flush(true)
    started.future.flatMap { _ =>
      destination.storeResult.set(() => Future.successful(SyncSuccess.emptyNow()))
      val second = buffered.storeHistoryJson(key, "second")
      buffered.flush(true)
      assertEquals(destination.writes.now(), List(Map(key -> "first")))
      gate.failure(IllegalStateException("first failed"))
      first.failed.flatMap(_ => second).map(_ => assertEquals(destination.writes.now(), List(Map(key -> "first"), Map(key -> "second"))))
    }
  }
  test("the reader/writer adapter registers bulk requests before flushing") {
    val destination = new Destination
    val bound = SyncInformation(destination.definition, SyncStrategy.SYNC_EVERYTHING).forContext(context, _ => destination.toBuffered)
    val writing = bound.writer.writeAll(bound.handler.syncLogger, Map(key -> "one", context.toSyncContext("other") -> "two"))
    bound.writer.flush(bound.handler.syncLogger, true)
    writing.map(_ => assertEquals(destination.writes.now().head.size, 2))
  }
}
