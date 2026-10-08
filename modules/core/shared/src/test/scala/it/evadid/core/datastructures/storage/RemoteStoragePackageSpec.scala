package it.evadid.core.datastructures.storage

import it.evadid.core.datastructures.storage.RemoteCacheCollection.CacheKey
import it.evadid.core.datastructures.storage.RemoteSyncDataCache.*
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.{PrintToStdLogger, SyncLogger}
import it.evadid.workbook.interaction.sync.SyncSuccess
import munit.FunSuite

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class RemoteStoragePackageSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val logger = SyncLogger(Logger.withNameAndPrefixes(printMap = PrintToStdLogger.printNothing))
  private val time = LocalDateTime.of(2026, 1, 1, 0, 0)

  private class Destination extends CacheKey[String, String] with RemoteCacheConfig[String, String] {
    var fetchCount = 0
    var writes = List.empty[Map[String, String]]
    var fetched: Future[FetchResponse[String, String]] = Future.successful(
      FetchResponse.fromMap(time, Map("a" -> "remote"), _ => Some(time.minusSeconds(1))))
    var writeResult: Future[SyncSuccess] = Future.successful(SyncSuccess(1, 0, 0, time))
    override val syncLogger = logger
    override val reader = new RemoteDataReader[String, String] {
      override def fetchAll(logger: SyncLogger): Future[FetchResponse[String, String]] = {
        fetchCount += 1
        fetched
      }
      override def fetchByKey(logger: SyncLogger, key: String): Future[FetchResponse[String, String]] = fetchAll(logger)
    }
    override val writer = new RemoteDataWriter[String, String] {
      override def writeAll(logger: SyncLogger, values: Map[String, String]): Future[SyncSuccess] = {
        writes = writes :+ values
        writeResult
      }
      override def writeForKey(logger: SyncLogger, key: String, value: String): Future[SyncSuccess] = writeAll(logger, Map(key -> value))
    }
  }

  private def populated(destination: Destination) = RemoteSyncDataCache(destination, Some(time),
    Set(DataEntryReadFromServer("a", "remote", time.minusSeconds(1))))

  test("SyncStatus compares timestamps strictly, including missing timestamps") {
    val entry = DataEntryReadFromServer("a", "v", time.minusSeconds(1))
    val status = SyncStatus(Some(time), "a", Some(entry))
    assertEquals(entry.timestamp, time.minusSeconds(1))
    val toWrite = DataEntryToWriteToServer("a", "new", time)
    assertEquals(toWrite.timestamp, time)
    assert(!status.isSubmittedTimeNewerThanLastRequest(time))
    assert(status.isSubmittedTimeNewerThanLastRequest(time.plusNanos(1)))
    assert(!status.isSubmittedTimeNewerThanLastChangedTimestamp(time.minusSeconds(1)))
    assert(status.isSubmittedTimeNewerThanLastChangedTimestamp(time))
    val missing = SyncStatus[String, String](None, "missing", None)
    assert(missing.isSubmittedTimeNewerThanLastRequest(time))
    assert(missing.isSubmittedTimeNewerThanLastChangedTimestamp(time))
  }

  test("FetchResponse omits values whose timestamps cannot be read") {
    val response = FetchResponse.fromMap(time, Map("valid" -> "yes", "invalid" -> "no"),
      (value: String) => if (value == "yes") Some(time.minusDays(1)) else None)
    assertEquals(response.timestampFetchResponse, time)
    assertEquals(response.fetchedValues, Set(DataEntryReadFromServer("valid", "yes", time.minusDays(1))))
  }

  test("RemoteSyncDataCache adds entries immutably and never moves its fetch time backwards") {
    val destination = new Destination
    val cache = populated(destination)
    val added = cache.addAll(Set(DataEntryReadFromServer("b", "new", time)), time.minusDays(1))
    assertEquals(added.lastCacheUpdate, Some(time))
    assertEquals(added.asMap.keySet, Set("a", "b"))
    assertEquals(cache.asMap.keySet, Set("a"))
    assertEquals(cache.chooseNewerTimestamp(time.plusDays(1)), Some(time.plusDays(1)))
    assertEquals(cache.getCurrentSyncStatus("a").lastKnownRemoteValue.map(_.dataValue), Some("remote"))
    assertEquals(cache.getCurrentSyncStatus("missing"), SyncStatus[String, String](None, "missing", None))
  }

  test("RemoteSyncDataCache writes only new entries and preserves writer failures") {
    val destination = new Destination
    val cache = populated(destination)
    val stale = DataEntryToWriteToServer("a", "old", time.minusSeconds(2))
    val fresh = DataEntryToWriteToServer("a", "new", time.plusSeconds(1))
    val missing = DataEntryToWriteToServer("b", "value", time.minusDays(1))
    assert(!cache.needsWriting(stale))
    assert(cache.needsWriting(fresh))
    assert(cache.needsWriting(missing))
    cache.writeIfNecessary(List(stale)).flatMap { empty =>
      assertEquals(empty.elementsAdded, 0)
      assertEquals(destination.writes, Nil)
      cache.writeIfNecessary(List(stale, fresh, missing)).flatMap { result =>
        assertEquals(result, SyncSuccess(1, 0, 0, time))
        assertEquals(destination.writes, List(Map("a" -> "new", "b" -> "value")))
        val error = new IllegalStateException("write rejected")
        destination.writeResult = Future.failed(error)
        cache.writeIfNecessary(List(fresh)).failed.map(e => assert(e eq error))
      }
    }
  }

  test("RemoteSyncDataCache fetches once when stale, reuses fresh data and recovers only in the safe API") {
    val destination = new Destination
    val empty = RemoteSyncDataCache[String, String](destination, None, Set.empty)
    empty.ensureCacheIsAtLeastThisRecent(time).flatMap { fetched =>
      assertEquals(destination.fetchCount, 1)
      assertEquals(fetched.asMap("a").dataValue, "remote")
      fetched.ensureCacheIsAtLeastThisRecent(time).flatMap { reused =>
        assertEquals(reused, fetched)
        assertEquals(destination.fetchCount, 1)
        val error = new IllegalStateException("offline")
        destination.fetched = Future.failed(error)
        fetched.ensureCacheIsAtLeastThisRecent(time.plusSeconds(1)).failed.flatMap { e =>
          assert(e eq error)
          fetched.tryEnsureCacheIsAtLeastThisRecent(time.plusSeconds(1)).map(result => assertEquals(result, fetched))
        }
      }
    }
  }

  test("RemoteCacheCollection and CacheCollectionReport retain destinations, statuses and keys") {
    val first = new Destination
    val second = new Destination
    val empty = RemoteCacheCollection.fromCacheKeys[String, String, Destination](logger, List(first, second))
    assertEquals(empty.ageOfCaches, Map(first -> None, second -> None))
    assertEquals(empty.allKnownKeys(), Set.empty[String])
    val collection = empty.copy(remoteCaches = empty.remoteCaches.updated(first, populated(first)))
    assertEquals(collection.allKnownKeys(), Set("a"))
    val report = collection.createReportFor("a")
    assertEquals(report.key, "a")
    assertEquals(report.allAvailableCacheKeys.toSet, Set(first, second))
    assertEquals(report.cacheStatus(first).lastKnownRemoteValue.map(_.dataValue), Some("remote"))
    assertEquals(report.cacheStatus(second).lastKnownRemoteValue, None)
    assertEquals(collection.mapAllCaches(_.knownEntries.size).sorted, List(0, 1))
    assertEquals(collection.mapAllCachesWithKey((key, _) => key).toSet, Set[CacheKey[String, String]](first, second))
    var visited = Set.empty[CacheKey[String, String]]
    collection.forAllCachesWithKey((key, _) => visited += key)
    assertEquals(visited, Set[CacheKey[String, String]](first, second))
    var count = 0
    collection.forAllCaches(_ => count += 1)
    assertEquals(count, 2)
  }

  test("asynchronous collection mappings preserve both destinations and auxiliary outputs") {
    val first = new Destination
    val second = new Destination
    val collection = RemoteCacheCollection.fromCacheKeys[String, String, Destination](logger, List(first, second))
    collection.mapAllAsync(_.ensureCacheIsAtLeastThisRecent(time)).flatMap { fetched =>
      assertEquals(fetched.allKnownKeys(), Set("a"))
      fetched.mapAllAsyncWithKeyAndOutput((key, cache) => Future.successful((cache, key == first))).map { (updated, output) =>
        assertEquals(updated.remoteCaches.keySet, Set(first, second))
        assertEquals(output, Map(first -> true, second -> false))
      }
    }
  }

  test("collection store refreshes data even after a write failure and skips empty requests") {
    val destination = new Destination
    destination.writeResult = Future.failed(new IllegalStateException("offline"))
    val collection = RemoteCacheCollection.fromCacheKeys[String, String, Destination](logger, List(destination))
    collection.requestCacheDependentStore(_ => Nil).flatMap { skipped =>
      assertEquals(skipped.remoteCaches, collection.remoteCaches)
      assertEquals(destination.fetchCount, 0)
      collection.requestCacheDependentStore(_ => List(DataEntryToWriteToServer("a", "new", time))).map { refreshed =>
        assertEquals(destination.fetchCount, 1)
        assertEquals(refreshed.allKnownKeys(), Set("a"))
      }
    }
  }
}
