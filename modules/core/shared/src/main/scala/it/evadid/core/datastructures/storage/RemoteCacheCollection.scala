package it.evadid.core.datastructures.storage

import upickle.default.ReadWriter
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.core.datastructures.storage.RemoteCacheCollection.{CacheCollectionReport, CacheKey}
import it.evadid.core.datastructures.storage.RemoteSyncDataCache.*
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.model.{DataEntryToSync, SyncStatus}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

case class RemoteCacheCollection[K , D , CK <: CacheKey[K, D]](baseLogger: SyncLogger, remoteCaches: Map[CK, RemoteSyncDataCache[K, D]]) {

  private given ExecutionContext = ExecutionContext.global

  def createReportFor(key: K): CacheCollectionReport[K, D, CK] = {
    val resMap = remoteCaches.keys.map(curCacheKey => {
      val resStatus: SyncStatus[K, D] = remoteCaches(curCacheKey).getCurrentSyncStatus(key)
      curCacheKey -> resStatus
    }).toMap
    CacheCollectionReport(key, resMap, remoteCaches.keys.toList)
  }

  def allKnownKeys(): Set[K] = remoteCaches.values.flatMap(_.asMap.values.map(_.dataKey)).toSet

  def forAllCachesWithKey(func: (CacheKey[K, D], RemoteSyncDataCache[K, D]) => Any): Unit = {
    remoteCaches.iterator.foreach(tup => func.apply(tup._1, tup._2))
  }

  def forAllCaches(func: RemoteSyncDataCache[K, D] => Any): Unit = {
    remoteCaches.values.foreach(func)
  }

  def mapAllCaches[O](func: RemoteSyncDataCache[K, D] => O): List[O] = {
    remoteCaches.values.map(func).toList
  }

  def mapAllCachesWithKey[O](func: (CacheKey[K, D], RemoteSyncDataCache[K, D]) => O): List[O] =
    remoteCaches.iterator.map(tup => func.apply(tup._1, tup._2)).toList

  def mapAllAsync(func: RemoteSyncDataCache[K, D] => Future[RemoteSyncDataCache[K, D]]): Future[RemoteCacheCollection[K, D, CK]] =
    mapAllAsyncWithKey((_, cache) => func(cache))

  def mapAllAsyncWithKey(func: (CK, RemoteSyncDataCache[K, D]) => Future[RemoteSyncDataCache[K, D]]): Future[RemoteCacheCollection[K, D, CK]] =
    Future.traverse(remoteCaches.iterator) { (key, cache) =>
      func(key, cache).map(key -> _)
    }.map(updated => copy(remoteCaches = updated.toMap))

  def mapAllAsyncWithKeyAndOutput[O](func: (CK, RemoteSyncDataCache[K, D]) => Future[(RemoteSyncDataCache[K, D], O)]): Future[(RemoteCacheCollection[K, D, CK], Map[CK, O])] =
    Future.traverse(remoteCaches.iterator) { (key, cache) =>
      func(key, cache).map { (updated, output) => (key, updated, output) }
    }.map { completed =>
      val results = completed.toList
      val updated = results.map { case (key, cache, _) => key -> cache }.toMap
      val output = results.map { case (key, _, value) => key -> value }.toMap
      (copy(remoteCaches = updated), output)
    }

  def requestCacheDependentUpdate(func: CK => LocalDateTime): Future[RemoteCacheCollection[K, D, CK]] = {
    mapAllAsyncWithKey((cacheKey, cache) => cache.ensureCacheIsAtLeastThisRecent(func(cacheKey)))
  }

  def requestCacheDependentStore(func: CK => List[DataEntryToSync[K, D]]): Future[RemoteCacheCollection[K, D, CK]] =
    mapAllAsyncWithKey { (key, cache) =>
      val toWrite = func(key)
      if toWrite.isEmpty then Future.successful(cache)
      else {
        val ensureCacheUntil = toWrite.map(_.timestamp).max
        // Refresh after either write outcome; a failed refresh still propagates.
        cache.writeIfNecessary(toWrite).transformWith(_ => cache.ensureCacheIsAtLeastThisRecent(ensureCacheUntil))
      }
    }

  def ensureCachesAreAtLeastThisRecent(maxAge: LocalDateTime): Future[RemoteCacheCollection[K, D, CK]] = {
    mapAllAsync(_.tryEnsureCacheIsAtLeastThisRecent(maxAge))
  }

  lazy val ageOfCaches: Map[CK, Option[LocalDateTime]] = remoteCaches.iterator.map(tup => tup._1 -> tup._2.lastCacheUpdate).toMap

}

object RemoteCacheCollection {


  def fromCacheKeys[K , D , CK <: CacheKey[K, D]](baseLogger: SyncLogger, keyForCaches: List[CK]): RemoteCacheCollection[K, D, CK] = {
    def buildRemoteCache(cacheKey: CK): (CK, RemoteSyncDataCache[K, D]) = {

      val config = new RemoteCacheConfig[K, D]() {
        override def reader: RemoteDataReader[K, D] = cacheKey.reader

        override def writer: RemoteDataWriter[K, D] = cacheKey.writer

        override def syncLogger: SyncLogger = baseLogger.forSyncDest(cacheKey.toString)

      }
      (cacheKey, RemoteSyncDataCache(config, None, Set()))
    }

    val remoteCaches: Map[CK, RemoteSyncDataCache[K, D]] = keyForCaches.map(buildRemoteCache).toMap
    RemoteCacheCollection(baseLogger, remoteCaches)
  }

  trait CacheKey[K , D ] {
    def reader: RemoteDataReader[K, D]

    def writer: RemoteDataWriter[K, D]
  }

  case class CacheCollectionReport[K, D, CK <: CacheKey[K, D]](key: K, cacheStatus: Map[CK, SyncStatus[K, D]], allAvailableCacheKeys: List[CK]) derives ReadWriter

}

