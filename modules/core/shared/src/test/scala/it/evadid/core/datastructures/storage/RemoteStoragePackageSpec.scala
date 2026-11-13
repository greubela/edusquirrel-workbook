package it.evadid.core.datastructures.storage

import it.evadid.core.datastructures.storage.RemoteCacheCollection.CacheKey
import it.evadid.core.datastructures.storage.RemoteSyncDataCache.*
import it.evadid.workbook.interaction.sync.model.*
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.{PrintToStdLogger, SyncLogger}
import it.evadid.workbook.interaction.sync.SyncSuccess
import munit.FunSuite

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class RemoteStoragePackageSpec extends FunSuite {
  private def DataEntryReadFromServer[K, D](key: K, value: D, time: java.time.LocalDateTime): DataEntryToSync[K, D] =
    DataEntryToSync(key, value, time, SyncDirection.SERVER_TO_CLIENT)
  private def DataEntryToWriteToServer[K, D](key: K, value: D, time: java.time.LocalDateTime): DataEntryToSync[K, D] =
    DataEntryToSync(key, value, time, SyncDirection.CLIENT_TO_SERVER)

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

  test("cache lookups and merged snapshots consistently select the newest version per key") {
    val destination = new Destination
    val oldest = DataEntryReadFromServer("a", "oldest", time.minusSeconds(3))
    val newest = DataEntryReadFromServer("a", "newest", time.minusSeconds(1))
    val middle = DataEntryReadFromServer("a", "middle", time.minusSeconds(2))
    val cache = RemoteSyncDataCache(destination, Some(time), Set(oldest, newest, middle))
    assertEquals(cache.asMap("a"), newest)
    assertEquals(cache.getCurrentSyncStatus("a").lastKnownRemoteValue, Some(newest))
    assert(!cache.needsWriting(DataEntryToWriteToServer("a", "middle", middle.timestamp)))
    val merged = cache.addAll(Set(oldest, DataEntryReadFromServer("b", "other", time)), time.minusDays(1))
    assertEquals(merged.knownEntries, Set(newest, DataEntryReadFromServer("b", "other", time)))
    assertEquals(merged.lastCacheUpdate, Some(time))
    assertEquals(cache.knownEntries.size, 3)
  }

  test("cache rejects conflicting newest timestamps but ignores obsolete conflicts") {
    val destination = new Destination
    val first = DataEntryReadFromServer("a", "first", time)
    val conflict = DataEntryReadFromServer("a", "conflict", time)
    val cache = RemoteSyncDataCache(destination, Some(time), Set(first, conflict))
    intercept[IllegalArgumentException](cache.asMap)
    val newer = DataEntryReadFromServer("a", "newer", time.plusSeconds(1))
    assertEquals(cache.addAll(Set(newer), time).asMap("a"), newer)
    val valid = populated(destination)
    valid.writeIfNecessary(List(first, conflict)).failed.map { error =>
      assert(error.getMessage.contains("Conflicting cache entries"))
      assertEquals(destination.writes, Nil)
    }
  }

  test("write batches select the latest version independently of list order") {
    val destination = new Destination
    val cache = populated(destination)
    val newer = DataEntryToWriteToServer("a", "newer", time.plusSeconds(2))
    val older = DataEntryToWriteToServer("a", "older", time.plusSeconds(1))
    cache.writeIfNecessary(List(newer, older)).flatMap { _ =>
      cache.writeIfNecessary(List(older, newer)).map { _ =>
        assertEquals(destination.writes, List.fill(2)(Map("a" -> "newer")))
      }
    }
  }

  test("synchronous reader and writer exceptions are returned as failed futures") {
    val error = new IllegalStateException("transport threw")
    val config = new RemoteCacheConfig[String, String] {
      val syncLogger = logger
      val reader = new RemoteDataReader[String, String] {
        def fetchAll(logger: SyncLogger): Future[FetchResponse[String, String]] = throw error
        def fetchByKey(logger: SyncLogger, key: String): Future[FetchResponse[String, String]] = throw error
      }
      val writer = new RemoteDataWriter[String, String] {
        def writeAll(logger: SyncLogger, values: Map[String, String]): Future[SyncSuccess] = throw error
        def writeForKey(logger: SyncLogger, key: String, value: String): Future[SyncSuccess] = throw error
      }
    }
    val cache = RemoteSyncDataCache(config, None, Set.empty[DataEntryToSync[String, String]])
    cache.ensureCacheIsAtLeastThisRecent(time).failed.flatMap { failure =>
      assert(failure eq error)
      cache.tryEnsureCacheIsAtLeastThisRecent(time).flatMap { unchanged =>
        assert(unchanged eq cache)
        cache.writeIfNecessary(List(DataEntryToWriteToServer("a", "new", time))).failed.map(failure => assert(failure eq error))
      }
    }
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

  test("collection store preserves write failures, skips empty requests and allows retry") {
    val destination = new Destination
    val error = new IllegalStateException("offline")
    destination.writeResult = Future.failed(error)
    val collection = RemoteCacheCollection.fromCacheKeys[String, String, Destination](logger, List(destination))
    collection.requestCacheDependentStore(_ => Nil).flatMap { skipped =>
      assertEquals(skipped.remoteCaches, collection.remoteCaches)
      assertEquals(destination.fetchCount, 0)
      val entries = List(DataEntryToWriteToServer("a", "new", time))
      collection.requestCacheDependentStore(_ => entries).failed.flatMap { failure =>
        assert(failure eq error)
        assertEquals(destination.fetchCount, 0, "a read must not conceal a failed store")
        destination.writeResult = Future.successful(SyncSuccess(1, 0, 0, time))
        destination.fetched = Future.successful(FetchResponse.fromMap(time.plusSeconds(1), Map("a" -> "new"), _ => Some(time)))
        collection.requestCacheDependentStore(_ => entries).map { retried =>
          assertEquals(retried.remoteCaches(destination).asMap("a").dataValue, "new")
          assertEquals(destination.writes, List.fill(2)(Map("a" -> "new")))
        }
      }
    }
  }

  test("a successful store refreshes even when its event predates the last cache request") {
    val destination = new Destination
    val cache = populated(destination)
    val changed = DataEntryToWriteToServer("a", "with feedback", time.minusSeconds(1))
    assert(cache.needsWriting(changed), "equal timestamps do not imply equal payloads")
    destination.fetched = Future.successful(FetchResponse.fromMap(time.plusSeconds(1), Map("a" -> "with feedback"), _ => Some(changed.timestamp)))
    val collection = RemoteCacheCollection(logger, Map(destination -> cache))
    collection.requestCacheDependentStore(_ => List(changed)).map { updated =>
      assertEquals(destination.fetchCount, 1)
      assertEquals(updated.remoteCaches(destination).asMap("a").dataValue, "with feedback")
      assert(!updated.remoteCaches(destination).needsWriting(changed))
      assertEquals(cache.asMap("a").dataValue, "remote", "previous snapshots remain immutable")
    }
  }

  test("collection store refresh failures propagate and empty destinations stay empty") {
    val destination = new Destination
    val error = new IllegalStateException("refresh failed")
    destination.fetched = Future.failed(error)
    val collection = RemoteCacheCollection.fromCacheKeys[String, String, Destination](logger, List(destination))
    collection.requestCacheDependentStore(_ => List(DataEntryToWriteToServer("a", "new", time))).failed.flatMap { failure =>
      assert(failure eq error)
      assertEquals(destination.fetchCount, 1)
      assertEquals(destination.writes, List(Map("a" -> "new")))
      RemoteCacheCollection.fromCacheKeys[String, String, Destination](logger, Nil)
        .requestCacheDependentStore(_ => fail("empty collection invoked callback")).map(result => assertEquals(result.remoteCaches.size, 0))
    }
  }

}
