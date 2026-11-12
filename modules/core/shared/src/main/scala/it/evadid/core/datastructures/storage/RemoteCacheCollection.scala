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
    val statuses = remoteCaches.map((destination, cache) => destination -> cache.getCurrentSyncStatus(key))
    CacheCollectionReport(key, statuses, remoteCaches.keys.toList)
  }

  def allKnownKeys(): Set[K] = remoteCaches.valuesIterator.flatMap(_.asMap.keysIterator).toSet

  def forAllCachesWithKey(func: (CacheKey[K, D], RemoteSyncDataCache[K, D]) => Any): Unit = {
    remoteCaches.iterator.foreach((destination, cache) => func(destination, cache))
  }

  def forAllCaches(func: RemoteSyncDataCache[K, D] => Any): Unit = {
    remoteCaches.values.foreach(func)
  }

  def mapAllCaches[O](func: RemoteSyncDataCache[K, D] => O): List[O] = {
    remoteCaches.values.map(func).toList
  }

  def mapAllCachesWithKey[O](func: (CacheKey[K, D], RemoteSyncDataCache[K, D]) => O): List[O] =
    remoteCaches.iterator.map((destination, cache) => func(destination, cache)).toList

  def mapAllAsync(func: RemoteSyncDataCache[K, D] => Future[RemoteSyncDataCache[K, D]]): Future[RemoteCacheCollection[K, D, CK]] = {
    mapAllAsyncWithKey((_, cache) => func(cache))
  }

  def mapAllAsyncWithKey(func: (CK, RemoteSyncDataCache[K, D]) => Future[RemoteSyncDataCache[K, D]]): Future[RemoteCacheCollection[K, D, CK]] =
    Future.traverse(remoteCaches.toList) { (destination, cache) =>
      func(destination, cache).map(destination -> _)
    }.map(updated => copy(remoteCaches = updated.toMap))

  def mapAllAsyncWithKeyAndOutput[O](func: (CK, RemoteSyncDataCache[K, D]) => Future[(RemoteSyncDataCache[K, D], O)]): Future[(RemoteCacheCollection[K, D, CK], Map[CK, O])] =
    Future.traverse(remoteCaches.toList) { (destination, cache) =>
      func(destination, cache).map { (updated, output) => (destination, updated, output) }
    }.map { results =>
      val caches = results.map((destination, updated, _) => destination -> updated).toMap
      val outputs = results.map((destination, _, output) => destination -> output).toMap
      copy(remoteCaches = caches) -> outputs
    }

  def requestCacheDependentUpdate(func: CK => LocalDateTime): Future[RemoteCacheCollection[K, D, CK]] = {
    mapAllAsyncWithKey((cacheKey, cache) => cache.ensureCacheIsAtLeastThisRecent(func(cacheKey)))
  }

  def requestCacheDependentStore(func: CK => List[DataEntryToSync[K, D]]): Future[RemoteCacheCollection[K, D, CK]] =
    mapAllAsyncWithKey((key, cache) => storeAndRefresh(cache, func(key)))

  private def storeAndRefresh(cache: RemoteSyncDataCache[K, D], entries: List[DataEntryToSync[K, D]]): Future[RemoteSyncDataCache[K, D]] =
    if entries.isEmpty then Future.successful(cache)
    else {
      val requiredAge = entries.map(_.timestamp).max
      // Refresh after either outcome: the remote state may have changed despite a failed response.
      cache.writeIfNecessary(entries).transformWith(_ => cache.ensureCacheIsAtLeastThisRecent(requiredAge))
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

