package it.evadid.core.datastructures.storage

import it.evadid.core.datastructures.storage.RemoteSyncDataCache.*
import it.evadid.core.util.InfoUtil
import it.evadid.util.logging.LoggingLevel.INFO
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.SyncInformation.SyncSuccess

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}


case class RemoteSyncDataCache[K, D](config: RemoteCacheConfig[K, D], lastCacheUpdate: Option[LocalDateTime], knownEntries: Set[DataEntryReadFromServer[K, D]]) {

  private given ExecutionContext = ExecutionContext.global

  lazy val syncLock: RemoteSyncDataCache[K, D] = this

  def writeIfNecessary(writeRequested: List[DataEntryToWriteToServer[K, D]]): Future[SyncSuccess] = {
    if (writeRequested.isEmpty) Future.successful(SyncSuccess.emptyNow())
    else syncLock.synchronized {
      val elementsToWrite = writeRequested.filter(needsWriting)
      if (elementsToWrite.isEmpty) {
        Future.successful(SyncSuccess.emptyNow())
      } else {
        val mapToWrite = elementsToWrite.map(el => el.dataKey -> el.dataValue).toMap
        val lastToWrite = elementsToWrite.maxByOption(_.timestampDataCreated).map(_.timestampDataCreated)
        val msg1: String = s"Need to write ${elementsToWrite.size} elements (${writeRequested.size} total requests): elements out of sync."
        val msg2: String = s"Cache Time ${InfoUtil.datetimeFormattedForLog(lastCacheUpdate)} / latest element time ${InfoUtil.datetimeFormattedForLog(lastToWrite)}}"
        config.syncLogger.log(msg1 + " " + msg2, INFO, Some(true))
        config.writer.writeAll(mapToWrite)
      }
    }
  }

  def needsWriting(entry: DataEntryToWriteToServer[K, D]): Boolean = {
    val curSyncStatus = getCurrentSyncStatus(entry.dataKey)
    val isEntryNewerThanCache = curSyncStatus.isSubmittedTimeNewerThanLastRequest(entry.timestampDataCreated)
    val isEntryNewerThanLastKnown = curSyncStatus.isSubmittedTimeNewerThanLastChangedTimestamp(entry.timestampDataCreated)

    config.syncLogger.logInfo(s"needsWriting for entry at ${entry.timestampDataCreated} (${entry.dataValue})--> lastCacheUpdate: ${lastCacheUpdate} (newer: ${isEntryNewerThanCache}), lastKnown: ${curSyncStatus.lastKnownRemoteValue.map(_.timestampDataLastChanged)} (newer: ${isEntryNewerThanLastKnown}), value: ${curSyncStatus.lastKnownRemoteValue.map(_.dataValue)}")

    isEntryNewerThanLastKnown || isEntryNewerThanCache
  }

  def chooseNewerTimestamp(other: LocalDateTime): Option[LocalDateTime] = {
    if (lastCacheUpdate.nonEmpty && lastCacheUpdate.get.isAfter(other)) lastCacheUpdate else Some(other)
  }

  def addAll(set: Set[DataEntryReadFromServer[K, D]], remoteTimestamp: LocalDateTime): RemoteSyncDataCache[K, D] = syncLock.synchronized {
    RemoteSyncDataCache(config, chooseNewerTimestamp(remoteTimestamp), knownEntries ++ set)
  }

  /*def addCacheEntry(key: K, value: D, valueTimestamp: LocalDateTime = LocalDateTime.now()): RemoteSyncDataCache[K, D] = syncLock.synchronized {
    val newEntries = knownEntries.filterNot(_.dataKey == key) ++ List(DataEntryReadFromServer(key, value, timestamp))
    RemoteSyncDataCache(config, newEntries)
  }*/

  lazy val asMap: Map[K, DataEntryReadFromServer[K, D]] = syncLock.synchronized {
    knownEntries.map(en => (en.dataKey, en)).toMap
  }


  def getCurrentSyncStatus(key: K): SyncStatus[K, D] = syncLock.synchronized {
    knownEntries.find(_.dataKey == key).match {
      case Some(entry) => SyncStatus(lastCacheUpdate, key, Some(entry))
      case None => SyncStatus(None, key, None)
    }
  }

  /*def ensureKeyWithMaxAge(key: K, maxAge: LocalDateTime): Future[RemoteSyncDataCache[K, D]] = syncLock.synchronized {
    knownEntries.find(_.dataKey == key).match {
      case Some(entry) if entry.timestampDataLastChanged.isBefore(maxAge) =>
        config.baseLogger.log(s"Cache for key ${key} is up to date (${entry}), no need to update!", INFO, Some(false))
        Future.successful(this)
      case Some(entry) =>
        config.baseLogger.log(s"Oldest element for key ${key} is outdated (${entry}), requesting fetch!", INFO, Some(false))
        executeFetch()
      case None =>
        config.baseLogger.log(s"No value in Cache for key ${key}, requesting fetch!", INFO, Some(false))
        executeFetch()
    }
  }*/

  def tryEnsureCacheIsAtLeastThisRecent(maxAge: LocalDateTime): Future[RemoteSyncDataCache[K, D]] = syncLock.synchronized {
    ensureCacheIsAtLeastThisRecent(maxAge).recover {
      case err: Throwable =>
        println(s"[UGLY ERR] ${err.getMessage}")
        config.syncLogger.logExceptionWarn("Ignoring that ensureCacheIsAtLeastThisRecent failed because of", err)
        this
    }
  }

  def ensureCacheIsAtLeastThisRecent(maxAge: LocalDateTime): Future[RemoteSyncDataCache[K, D]] = syncLock.synchronized {
    if (lastCacheUpdate.isEmpty) {
      println(s"Cache was never updated, requesting fetch!")
      config.syncLogger.log("Cache was never updated, requesting fetch!", INFO, Some(false))
      executeFetch()
    } else if (lastCacheUpdate.get.isBefore(maxAge)) {
      println(s"Cache is outdated (last request${lastCacheUpdate.get} < ${maxAge}), requesting fetch!")
      config.syncLogger.log(s"Cache is outdated (last request${lastCacheUpdate.get} < ${maxAge}), requesting fetch!", INFO, Some(false))
      executeFetch()
    } else {
      println(s"No need to update cache, is still fresh enough (last request ${lastCacheUpdate.get} >= ${maxAge}")
      config.syncLogger.log(s"No need to update cache, is still fresh enough (last request ${lastCacheUpdate.get} >= ${maxAge}", INFO, Some(false))
      Future.successful(this)
    }
  }

  private def executeFetch(): Future[RemoteSyncDataCache[K, D]] = syncLock.synchronized {
    val futSet: Future[FetchResponse[K, D]] = config.reader.fetchAll()

    val futRes: Future[RemoteSyncDataCache[K, D]] = futSet.map(res => {
      this.addAll(res.fetchedValues, res.timestampFetchResponse)
    })
    futRes

  }


  private def formatKeyForLogging(key: K): String = syncLock.synchronized {
    key.toString
  }

  private def formatValueForLogging(value: D): String = syncLock.synchronized {
    value.toString
  }

}


object RemoteSyncDataCache {

  trait TimestampReader[T] {
    def readTimestamp(value: T): LocalDateTime
  }

  trait RemoteDataReader[K, D] {
    def fetchByKey(key: K): Future[FetchResponse[K, D]]

    def fetchAll(): Future[FetchResponse[K, D]]
  }

  trait RemoteDataWriter[K, D] {
    def writeForKey(key: K, dataValue: D): Future[SyncSuccess]

    def writeAll(map: Map[K, D]): Future[SyncSuccess]
  }


  sealed trait DataEntryToSync[K, D] {
    val dataKey: K
    val dataValue: D
    val timestamp: LocalDateTime
  }

  case class DataEntryReadFromServer[K, D](dataKey: K, dataValue: D, timestampDataLastChanged: LocalDateTime) {
    val timestamp: LocalDateTime = timestampDataLastChanged
  }

  case class DataEntryToWriteToServer[K, D](dataKey: K, dataValue: D, timestampDataCreated: LocalDateTime) {
    val timestamp: LocalDateTime = timestampDataCreated
  }

  trait FetchResponse[K, D] {
    def timestampFetchResponse: LocalDateTime

    def fetchedValues: Set[DataEntryReadFromServer[K, D]]

    protected def fetchedValuesFromMap(resMap: Map[K, D]): Set[DataEntryReadFromServer[K, D]] = fetchedValuesFromIterator(resMap.iterator)

    protected def fetchedValuesFromIterator(resIt: Iterator[(K, D)]): Set[DataEntryReadFromServer[K, D]] = {
      resIt.map(curTup => {
        DataEntryReadFromServer(curTup._1, curTup._2, timestampFetchResponse)
      }).toSet
    }
  }

  object FetchResponse {

    def fromMap[K, D](timestamp: LocalDateTime, map: Map[K, D], readTime: D => Option[LocalDateTime]): FetchResponse[K, D] = new FetchResponse[K, D]() {

      override def timestampFetchResponse: LocalDateTime = timestamp

      override def fetchedValues: Set[DataEntryReadFromServer[K, D]] = {
        map.iterator.flatMap(tup => {
          val time = readTime(tup._2)
          if (time.isEmpty) None
          else Some(DataEntryReadFromServer[K, D](tup._1, tup._2, time.get))
        }).toSet
      }
    }
  }


  case class SyncStatus[K, V](lastCacheRequest: Option[LocalDateTime], associatedKey: K, lastKnownRemoteValue: Option[DataEntryReadFromServer[K, V]]) {
    def isSubmittedTimeNewerThanLastRequest(timestamp: LocalDateTime): Boolean = if (lastCacheRequest.isEmpty) true else timestamp.isAfter(lastCacheRequest.get)

    def isSubmittedTimeNewerThanLastChangedTimestamp(timestamp: LocalDateTime): Boolean = if (lastKnownRemoteValue.isEmpty) true else timestamp.isAfter(lastKnownRemoteValue.get.timestampDataLastChanged)
  }

  trait RemoteCacheConfig[K, D] {
    def reader: RemoteDataReader[K, D]

    def writer: RemoteDataWriter[K, D]

    //def readTimestamp(value: D): LocalDateTime

    def syncLogger: SyncLogger
  }
}








