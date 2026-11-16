package it.evadid.core.datastructures.storage

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.storage.remote.{RemoteDataSynchronisation, RemoteReaderWriter}
import it.evadid.core.datastructures.storage.remote.RemoteReaderWriter.*
import it.evadid.util.logging.BasicLogger
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.SyncSuccess
import it.evadid.workbook.interaction.sync.model.FetchResponse
import munit.FunSuite
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future, Promise}

class RemoteDataSynchronisationSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val logger = SyncLogger(BasicLogger())
  private val time = LocalDateTime.of(2026, 1, 1, 0, 0)
  private class Destination extends RemoteReaderWriter[String, String] {
    val reads = State(List.empty[Set[String]])
    val writes = State(List.empty[Map[String, String]])
    val fetched = State[() => Future[FetchResponse[String, String]]](() => Future.successful(FetchResponse.fromMap(time, Map("a" -> "remote"))))
    val stored = State[() => Future[SyncSuccess]](() => Future.successful(SyncSuccess.emptyNow()))
    val reader = new RemoteDataReader[String, String] {
      def fetchByKey(logger: SyncLogger, key: String) = fetchByKeys(logger, Set(key))
      override def fetchByKeys(logger: SyncLogger, keys: Set[String]) = { reads.update(_ :+ keys); fetched.now()() }
      def fetchAll(logger: SyncLogger) = fetched.now()()
      def flush(logger: SyncLogger, forceFlush: Boolean): Unit = ()
    }
    val writer = new RemoteDataWriter[String, String] {
      def writeForKey(logger: SyncLogger, key: String, value: String) = writeAll(logger, Map(key -> value))
      def writeAll(logger: SyncLogger, values: Map[String, String]) = { writes.update(_ :+ values); stored.now()() }
      def flush(logger: SyncLogger, forceFlush: Boolean): Unit = ()
    }
  }

  test("cold caches fetch requested keys and publish before the returned future completes") {
    val destination = new Destination
    val sync = RemoteDataSynchronisation(logger, Set(destination))
    sync.ensureMaxAge(Set("a")).map { _ =>
      assertEquals(destination.reads.now(), List(Set("a")))
      assertEquals(sync.reports.now().get("a").lastKnownValues(destination).value, "remote")
      assertEquals(sync.updateReport.now(), Some(Map("a" -> "remote")))
    }
  }
  test("new and changed values are written and confirmed; unchanged values are skipped") {
    val destination = new Destination
    val sync = RemoteDataSynchronisation(logger, Set(destination))
    sync.requestStoreToAll(Map("a" -> "first")).flatMap { _ =>
      assertEquals(sync.reports.now().get("a").lastKnownValues(destination).value, "first")
      sync.requestStoreToAll(Map("a" -> "first"))
    }.flatMap(_ => sync.requestStoreToAll(Map("a" -> "second"))).map { _ =>
      assertEquals(destination.writes.now(), List(Map("a" -> "first"), Map("a" -> "second")))
      assertEquals(sync.reports.now().get("a").lastKnownValues(destination).value, "second")
    }
  }
  test("empty requests do not access destinations") {
    val destination = new Destination
    val sync = RemoteDataSynchronisation(logger, Set(destination))
    sync.ensureMaxAge(Set.empty).flatMap(_ => sync.requestStoreToAll(Map.empty)).map { _ =>
      assertEquals(destination.reads.now(), Nil)
      assertEquals(destination.writes.now(), Nil)
    }
  }
  test("synchronous read failures and failed writes remain retryable") {
    val destination = new Destination
    val sync = RemoteDataSynchronisation(logger, Set(destination))
    val failure = IllegalStateException("offline")
    destination.fetched.set(() => throw failure)
    sync.ensureMaxAge(Set("a")).failed.flatMap { error =>
      assert(error eq failure)
      destination.stored.set(() => Future.failed(failure))
      sync.requestStoreToAll(Map("a" -> "saved")).failed
    }.flatMap { error =>
      assert(error eq failure)
      assertEquals(sync.reports.now(), Some(Map.empty))
      destination.stored.set(() => Future.successful(SyncSuccess.emptyNow()))
      sync.requestStoreToAll(Map("a" -> "saved"))
    }.map(_ => assertEquals(sync.reports.now().get("a").lastKnownValues(destination).value, "saved"))
  }
  test("a delayed read cannot replace a later successful write or publish stale history") {
    val destination = new Destination
    val delayed = Promise[FetchResponse[String, String]]()
    destination.fetched.set(() => delayed.future)
    val sync = RemoteDataSynchronisation(logger, Set(destination))
    val reading = sync.ensureMaxAge(Set("a"))
    sync.requestStoreToAll(Map("a" -> "edited")).flatMap { _ =>
      delayed.success(FetchResponse.fromMap(time.plusDays(1), Map("a" -> "old")))
      reading
    }.map { _ =>
      assertEquals(sync.reports.now().get("a").lastKnownValues(destination).value, "edited")
      assertEquals(sync.updateReport.now(), Some(Map.empty))
    }
  }
  test("out of order write acknowledgements keep the newest submitted value") {
    val destination = new Destination
    val delayed = Promise[SyncSuccess]()
    destination.stored.set(() => delayed.future)
    val sync = RemoteDataSynchronisation(logger, Set(destination))
    val first = sync.requestStoreToAll(Map("a" -> "first"))
    destination.stored.set(() => Future.successful(SyncSuccess.emptyNow()))
    sync.requestStoreToAll(Map("a" -> "second")).flatMap { _ =>
      delayed.success(SyncSuccess.emptyNow())
      first
    }.map(_ => assertEquals(sync.reports.now().get("a").lastKnownValues(destination).value, "second"))
  }
  test("reverting an answer while a write is pending still submits the reverted value") {
    val destination = new Destination
    val sync = RemoteDataSynchronisation(logger, Set(destination))
    sync.requestStoreToAll(Map("a" -> "original")).flatMap { _ =>
      val delayed = Promise[SyncSuccess]()
      destination.stored.set(() => delayed.future)
      val pending = sync.requestStoreToAll(Map("a" -> "changed"))
      destination.stored.set(() => Future.successful(SyncSuccess.emptyNow()))
      sync.requestStoreToAll(Map("a" -> "original")).flatMap { _ =>
        delayed.success(SyncSuccess.emptyNow())
        pending
      }.map { _ =>
        assertEquals(destination.writes.now().map(_("a")), List("original", "changed", "original"))
        assertEquals(sync.reports.now().get("a").lastKnownValues(destination).value, "original")
      }
    }
  }

}
