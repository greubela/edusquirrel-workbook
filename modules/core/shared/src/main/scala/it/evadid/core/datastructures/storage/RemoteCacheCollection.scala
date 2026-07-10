package it.evadid.core.datastructures.storage

import it.evadid.core.datastructures.storage.RemoteCacheCollection.{CacheCollectionReport, CacheKey}
import it.evadid.core.datastructures.storage.RemoteSyncDataCache.*
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.SyncInformation.SyncSuccess

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class RemoteCacheCollection[K, D, CK <: CacheKey[K, D]](baseLogger: SyncLogger, remoteCaches: Map[CK, RemoteSyncDataCache[K, D]]) {

  private given ExecutionContext = ExecutionContext.global

  def createReportFor(key: K): CacheCollectionReport[K, D, CK] = {
    val resMap = remoteCaches.keys.map(curCacheKey => {
      val resStatus: SyncStatus[K, D] = remoteCaches(curCacheKey).getCurrentSyncStatus(key)
      curCacheKey -> resStatus
    }).toMap
    CacheCollectionReport(key, resMap)
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

  def mapAllAsync(func: RemoteSyncDataCache[K, D] => Future[RemoteSyncDataCache[K, D]]): Future[RemoteCacheCollection[K, D, CK]] = {
    val it: Iterator[(CK, RemoteSyncDataCache[K, D])] = remoteCaches.iterator
    val singleFut: Future[Iterator[(CK, RemoteSyncDataCache[K, D])]] = Future.traverse(it)(tup => func.apply(tup._2).map(res => tup._1 -> res))
    singleFut.map(res => RemoteCacheCollection[K, D, CK](baseLogger, res.toMap))
  }

  def mapAllAsyncWithKey(func: (CK, RemoteSyncDataCache[K, D]) => Future[RemoteSyncDataCache[K, D]]): Future[RemoteCacheCollection[K, D, CK]] = {
    val it: Iterator[(CK, RemoteSyncDataCache[K, D])] = remoteCaches.iterator
    val singleFut: Future[Iterator[(CK, RemoteSyncDataCache[K, D])]] = Future.traverse(it)(tup => func.apply(tup._1, tup._2).map(res => tup._1 -> res))
    singleFut.map(res => RemoteCacheCollection[K, D, CK](baseLogger, res.toMap))
  }

  def mapAllAsyncWithKeyAndOutput[O](func: (CK, RemoteSyncDataCache[K, D]) => Future[(RemoteSyncDataCache[K, D], O)]): Future[(RemoteCacheCollection[K, D, CK], Map[CK, O])] = {
    val it: Iterator[(CK, RemoteSyncDataCache[K, D])] = remoteCaches.iterator
    val singleFut: Future[Iterator[(CK, RemoteSyncDataCache[K, D], O)]] = Future.traverse(it)(tup => func.apply(tup._1, tup._2).map(res => (tup._1, res._1, res._2)))
    singleFut.map(res => {
      val updated = RemoteCacheCollection[K, D, CK](baseLogger, res.map(trip => trip._1 -> trip._2).toMap)
      val output = res.map(trip => trip._1 -> trip._3).toMap
      (updated, output)
    })
  }

  def requestCacheDependentUpdate(func: CK => LocalDateTime): Future[RemoteCacheCollection[K, D, CK]] = {
    mapAllAsyncWithKey((cacheKey, cache) => cache.ensureCacheIsAtLeastThisRecent(func(cacheKey)))
  }

  def requestCacheDependentStore(func: CK => List[DataEntryToWriteToServer[K, D]]): Future[RemoteCacheCollection[K, D, CK]] = {

    Future.traverse(remoteCaches.iterator)((cacheKey, cache) => {
      val toWrite = func(cacheKey)
      if(toWrite.isEmpty) Future.successful(cacheKey -> cache)
      else {
        val ensureCacheUntil = toWrite.map(_.timestampDataCreated).max
        cache.writeIfNecessary(toWrite).transformWith {
          case Success(syncSuccess) => cache.ensureCacheIsAtLeastThisRecent(ensureCacheUntil).map(cacheKey -> _)
          case Failure(error) => cache.ensureCacheIsAtLeastThisRecent(ensureCacheUntil).map(cacheKey -> _)
        }
      }
    }).map((res: Iterator[(CK, RemoteSyncDataCache[K, D])]) => RemoteCacheCollection(baseLogger, res.toMap))

    /*val res2: Future[(RemoteCacheCollection[K, D, CK], Map[CK, SyncSuccess])] = mapAllAsyncWithKeyAndOutput((key, cache) => cache.writeIfNecessary(func(key)))
    res.map(tup => tup._1 -> tup._2.values.foldLeft(SyncSuccess.emptyNow())(_.combine(_)))*/
  }

  def ensureCachesAreAtLeastThisRecent(maxAge: LocalDateTime): Future[RemoteCacheCollection[K, D, CK]] = {
    mapAllAsync(_.tryEnsureCacheIsAtLeastThisRecent(maxAge))
    //println("RemoteCacheCollection::70 --> ignoring ensure Cache request (:")
    //Future.successful(this)
  }

  lazy val ageOfCaches: Map[CK, Option[LocalDateTime]] = remoteCaches.iterator.map(tup => tup._1 -> tup._2.lastCacheUpdate).toMap

}

object RemoteCacheCollection {


  def fromCacheKeys[K, D, CK <: CacheKey[K, D]](baseLogger: SyncLogger, keyForCaches: List[CK]): RemoteCacheCollection[K, D, CK] = {
    def buildRemoteCache(cacheKey: CK): (CK, RemoteSyncDataCache[K, D]) = {

      val config = new RemoteCacheConfig[K, D]() {
        override def reader: RemoteDataReader[K, D] = cacheKey.reader

        override def writer: RemoteDataWriter[K, D] = cacheKey.writer

        override def syncLogger: SyncLogger = baseLogger.forSyncDest(cacheKey.toString)

        // override def readTimestamp(value: D): LocalDateTime = pReadTimestamp.apply(value)
      }
      (cacheKey, RemoteSyncDataCache(config, None, Set()))
    }

    val remoteCaches: Map[CK, RemoteSyncDataCache[K, D]] = keyForCaches.map(buildRemoteCache).toMap
    RemoteCacheCollection(baseLogger, remoteCaches)
  }

  trait CacheKey[K, D] {
    def reader: RemoteDataReader[K, D]

    def writer: RemoteDataWriter[K, D]
  }

  case class CacheCollectionReport[K, D, CK <: CacheKey[K, D]](key: K, cacheStatus: Map[CK, SyncStatus[K, D]])

}

