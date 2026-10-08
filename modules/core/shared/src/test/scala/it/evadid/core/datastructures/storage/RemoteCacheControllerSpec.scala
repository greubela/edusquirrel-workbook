package it.evadid.core.datastructures.storage

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.storage.RemoteCacheCollection.CacheKey
import it.evadid.core.datastructures.storage.RemoteSyncDataCache.*
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.{PrintToStdLogger, SyncLogger}
import it.evadid.workbook.interaction.sync.SyncSuccess
import munit.FunSuite

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future, Promise}

class RemoteCacheControllerSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val logger = SyncLogger(Logger.withNameAndPrefixes(printMap = PrintToStdLogger.printNothing))
  private val timestamp = LocalDateTime.of(2026, 10, 7, 12, 0)

  private class Destination(fetch: () => Future[FetchResponse[String, String]]) extends CacheKey[String, String] {
    var written = Map.empty[String, String]
    override val reader = new RemoteDataReader[String, String] {
      override def fetchAll(logger: SyncLogger): Future[FetchResponse[String, String]] = fetch()
      override def fetchByKey(logger: SyncLogger, key: String): Future[FetchResponse[String, String]] = fetch()
    }
    override val writer = new RemoteDataWriter[String, String] {
      override def writeAll(logger: SyncLogger, values: Map[String, String]): Future[SyncSuccess] = {
        written = written ++ values
        Future.successful(SyncSuccess.emptyNow())
      }
      override def writeForKey(logger: SyncLogger, key: String, value: String): Future[SyncSuccess] = writeAll(logger, Map(key -> value))
    }
  }

  private def response(value: String): FetchResponse[String, String] =
    FetchResponse.fromMap(timestamp, Map("program" -> value), _ => Some(timestamp))

  private def controller(destinations: State[List[Destination]]) =
    new RemoteCacheController[String, String, Destination](logger, destinations.observable) {
      override def onCacheKeyChange(newKeys: List[Destination], knownKeys: Set[String]): Future[?] = Future.successful(())
    }

  test("login installs the new destination before the immediate restore request") {
    val destinations = State(List.empty[Destination])
    val cache = controller(destinations)
    val local = new Destination(() => Future.successful(response("saved Snap XML")))
    assertEquals(cache.currentReport("program").allAvailableCacheKeys, List.empty[Destination])
    destinations.set(List(local))
    assertEquals(cache.currentReport("program").allAvailableCacheKeys, List(local))
    cache.ensureMaxAgeSafe(timestamp).map { _ =>
      assertEquals(cache.currentReport("program").cacheStatus(local).lastKnownRemoteValue.map(_.dataValue), Some("saved Snap XML"))
    }
  }

  test("a previous context's delayed fetch cannot replace the logged-in user's cache") {
    val delayed = Promise[FetchResponse[String, String]]()
    val old = new Destination(() => delayed.future)
    val destinations = State(List(old))
    val cache = controller(destinations)
    val oldFetch = cache.ensureMaxAgeSafe(timestamp)
    val local = new Destination(() => Future.successful(response("saved Snap XML")))
    destinations.set(List(local))
    cache.ensureMaxAgeSafe(timestamp).flatMap { _ =>
      delayed.success(response("previous user"))
      oldFetch.map { _ =>
        val report = cache.currentReport("program")
        assertEquals(report.allAvailableCacheKeys, List(local))
        assertEquals(report.cacheStatus(local).lastKnownRemoteValue.map(_.dataValue), Some("saved Snap XML"))
      }
    }
  }

  test("a save immediately after login writes to the new local destination") {
    val destinations = State(List.empty[Destination])
    val cache = controller(destinations)
    val local = new Destination(() => Future.successful(response("saved Snap XML")))
    assertEquals(cache.currentReport("program").allAvailableCacheKeys, List.empty[Destination])
    destinations.set(List(local))
    cache.requestStore(List(DataEntryToWriteToServer("program", "edited Snap XML", timestamp.plusSeconds(1)))).map { _ =>
      assertEquals(local.written, Map("program" -> "edited Snap XML"))
    }
  }

  test("an empty store request leaves the cache untouched and does not fetch or write") {
    var fetches = 0
    val local = new Destination(() => { fetches += 1; Future.successful(response("saved")) })
    val cache = controller(State(List(local)))
    cache.requestStore(Nil).map { _ =>
      assertEquals(fetches, 0)
      assertEquals(local.written, Map.empty[String, String])
      assertEquals(cache.currentReport("program").cacheStatus(local).lastKnownRemoteValue, None)
    }
  }

  test("observable reports publish a successful fetch and fresh caches are reused") {
    var fetches = 0
    val local = new Destination(() => { fetches += 1; Future.successful(response("saved")) })
    val cache = controller(State(List(local)))
    val report = cache.observableReport("program")
    cache.ensureMaxAgeSafe(timestamp).flatMap { _ =>
      assertEquals(report.now().get.cacheStatus(local).lastKnownRemoteValue.map(_.dataValue), Some("saved"))
      cache.ensureMaxAgeSafe(timestamp).map { _ =>
        assertEquals(fetches, 1)
        assertEquals(report.now().get, cache.currentReport("program"))
      }
    }
  }

  test("cache-dependent update propagates a fetch failure while safe refresh retains the cache") {
    val error = new IllegalStateException("offline")
    val local = new Destination(() => Future.failed(error))
    val cache = controller(State(List(local)))
    cache.requestCacheDependentUpdate(_ => timestamp).failed.flatMap { failed =>
      assert(failed eq error)
      cache.ensureMaxAgeSafe(timestamp).map { _ =>
        assertEquals(cache.currentReport("program").allAvailableCacheKeys, List(local))
        assertEquals(cache.currentReport("program").cacheStatus(local).lastKnownRemoteValue, None)
      }
    }
  }
}
