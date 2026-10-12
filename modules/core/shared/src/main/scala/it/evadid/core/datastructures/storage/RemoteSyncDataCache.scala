package it.evadid.core.datastructures.storage

import upickle.default.*
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.core.datastructures.storage.RemoteSyncDataCache.*
import it.evadid.core.datastructures.user.AllUserInfo
import it.evadid.core.util.InfoUtil
import it.evadid.util.logging.LoggingLevel.INFO
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.SyncInformation.*
import it.evadid.workbook.interaction.sync.SyncSuccess
import it.evadid.workbook.interaction.sync.model.DataEntryToSync.*
import it.evadid.workbook.interaction.sync.model.{DataEntryToSync, FetchResponse, SyncStatus}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}


case class RemoteSyncDataCache[K , D ](
                                      config: RemoteCacheConfig[K, D],
                                      lastCacheUpdate: Option[LocalDateTime],
                                      knownEntries: Set[DataEntryToSync[K, D]]
                                    ) {

  private given ExecutionContext = ExecutionContext.global

  lazy val syncLock: RemoteSyncDataCache[K, D] = this

  def writeIfNecessary(writeRequested: List[DataEntryToSync[K, D]]): Future[SyncSuccess] = {
    if (writeRequested.isEmpty) Future.successful(SyncSuccess.emptyNow())
    else syncLock.synchronized {
      val elementsToWrite = writeRequested.filter(needsWriting)
      if (elementsToWrite.isEmpty) {
        Future.successful(SyncSuccess.emptyNow())
      } else {
        val mapToWrite = elementsToWrite.map(el => el.dataKey -> el.dataValue).toMap
        val lastToWrite = elementsToWrite.maxByOption(_.timestamp).map(_.timestamp)
        val msg1: String = s"Need to write ${elementsToWrite.size} elements (${writeRequested.size} total requests): elements out of sync."
        val msg2: String = s"Cache Time ${InfoUtil.datetimeFormattedForLog(lastCacheUpdate)} / latest element time ${InfoUtil.datetimeFormattedForLog(lastToWrite)}}"
        config.syncLogger.log(msg1 + " " + msg2, INFO, Some(true))
        config.writer.writeAll(config.syncLogger, mapToWrite)
      }
    }
  }

  def needsWriting(entry: DataEntryToSync[K, D]): Boolean = {
    val curSyncStatus = getCurrentSyncStatus(entry.dataKey)
    val isEntryNewerThanCache = curSyncStatus.isSubmittedTimeNewerThanLastRequest(entry.timestamp)
    val isEntryNewerThanLastKnown = curSyncStatus.isSubmittedTimeNewerThanLastChangedTimestamp(entry.timestamp)

    config.syncLogger.logInfo(s"needsWriting for entry at ${entry.timestamp} (${entry.dataValue})--> lastCacheUpdate: ${lastCacheUpdate} (newer: ${isEntryNewerThanCache}), lastKnown: ${curSyncStatus.lastKnownRemoteValue.map(_.timestamp)} (newer: ${isEntryNewerThanLastKnown}), value: ${curSyncStatus.lastKnownRemoteValue.map(_.dataValue)}")

    isEntryNewerThanLastKnown || isEntryNewerThanCache
  }

  def chooseNewerTimestamp(other: LocalDateTime): Option[LocalDateTime] = {
    if (lastCacheUpdate.nonEmpty && lastCacheUpdate.get.isAfter(other)) lastCacheUpdate else Some(other)
  }

  def addAll(set: Set[DataEntryToSync[K, D]], remoteTimestamp: LocalDateTime): RemoteSyncDataCache[K, D] = syncLock.synchronized {
    RemoteSyncDataCache(config, chooseNewerTimestamp(remoteTimestamp), knownEntries ++ set)
  }

  /*def addCacheEntry(key: K, value: D, valueTimestamp: LocalDateTime = LocalDateTime.now()): RemoteSyncDataCache[K, D] = syncLock.synchronized {
    val newEntries = knownEntries.filterNot(_.dataKey == key) ++ List(DataEntryReadFromServer(key, value, timestamp))
    RemoteSyncDataCache(config, newEntries)
  }*/

  lazy val asMap: Map[K, DataEntryToSync[K, D]] = syncLock.synchronized {
    knownEntries.map(en => (en.dataKey, en)).toMap
  }


  def getCurrentSyncStatus(key: K): SyncStatus[K, D] = syncLock.synchronized {
    knownEntries.find(_.dataKey == key).match {
      case Some(entry) => SyncStatus(lastCacheUpdate, key, Some(entry))
      case None => SyncStatus(None, key, None)
    }
  }

  def tryEnsureCacheIsAtLeastThisRecent(maxAge: LocalDateTime): Future[RemoteSyncDataCache[K, D]] = syncLock.synchronized {
    ensureCacheIsAtLeastThisRecent(maxAge).recover {
      case err: Throwable =>
        config.syncLogger.logExceptionWarn("Ignoring that ensureCacheIsAtLeastThisRecent failed because of", err)
        this
    }
  }

  def ensureCacheIsAtLeastThisRecent(maxAge: LocalDateTime): Future[RemoteSyncDataCache[K, D]] = syncLock.synchronized {
    if (lastCacheUpdate.isEmpty) {
      //println(s"Cache was never updated, requesting fetch!")
      config.syncLogger.log("Cache was never updated, requesting fetch!", INFO, Some(false))
      executeFetch()
    } else if (lastCacheUpdate.get.isBefore(maxAge)) {
      //println(s"Cache is outdated (last request${lastCacheUpdate.get} < ${maxAge}), requesting fetch!")
      config.syncLogger.log(s"Cache is outdated (last request${lastCacheUpdate.get} < ${maxAge}), requesting fetch!", INFO, Some(false))
      executeFetch()
    } else {
      //println(s"No need to update cache, is still fresh enough (last request ${lastCacheUpdate.get} >= ${maxAge}")
      config.syncLogger.log(s"No need to update cache, is still fresh enough (last request ${lastCacheUpdate.get} >= ${maxAge}", INFO, Some(false))
      Future.successful(this)
    }
  }

  private def executeFetch(): Future[RemoteSyncDataCache[K, D]] = syncLock.synchronized {
    val futSet: Future[FetchResponse[K, D]] = config.reader.fetchAll(config.syncLogger)

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


  trait RemoteDataReader[K , D ] {
    def fetchByKey(logger: SyncLogger, key: K): Future[FetchResponse[K, D]]

    def fetchAll(logger: SyncLogger): Future[FetchResponse[K, D]]
  }

  trait RemoteDataWriter[K, D] {
    def writeForKey(logger: SyncLogger, key: K, dataValue: D): Future[SyncSuccess]

    def writeAll(logger: SyncLogger, map: Map[K, D]): Future[SyncSuccess]
  }

  trait RemoteCacheConfig[K, D] {
    def reader: RemoteDataReader[K, D]

    def writer: RemoteDataWriter[K, D]

    def syncLogger: SyncLogger
  }


}








