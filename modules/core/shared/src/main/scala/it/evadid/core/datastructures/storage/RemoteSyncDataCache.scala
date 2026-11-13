package it.evadid.core.datastructures.storage

import it.evadid.core.datastructures.storage.RemoteSyncDataCache.*
import it.evadid.core.util.InfoUtil
import it.evadid.util.logging.LoggingLevel.INFO
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.SyncSuccess
import it.evadid.workbook.interaction.sync.model.{DataEntryToSync, FetchResponse, SyncStatus}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try


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
      Try {
        val elementsToWrite = latestEntries(writeRequested).values.filter(needsWriting).toList
        if (elementsToWrite.isEmpty) {
          Future.successful(SyncSuccess.emptyNow())
        } else {
          val mapToWrite = elementsToWrite.map(el => el.dataKey -> el.dataValue).toMap
          val lastToWrite = elementsToWrite.maxByOption(_.timestamp).map(_.timestamp)
          val msg1: String = s"Need to write ${elementsToWrite.size} elements (${writeRequested.size} total requests): elements out of sync."
          val msg2: String = s"Cache Time ${InfoUtil.datetimeFormattedForLog(lastCacheUpdate)} / latest element time ${InfoUtil.datetimeFormattedForLog(lastToWrite)}"
          config.syncLogger.log(msg1 + " " + msg2, INFO, Some(true))
          config.writer.writeAll(config.syncLogger, mapToWrite)
        }
      }.fold(Future.failed, identity)
    }
  }

  def needsWriting(entry: DataEntryToSync[K, D]): Boolean = {
    val curSyncStatus = getCurrentSyncStatus(entry.dataKey)
    val isEntryNewerThanCache = curSyncStatus.isSubmittedTimeNewerThanLastRequest(entry.timestamp)
    val isEntryNewerThanLastKnown = curSyncStatus.isSubmittedTimeNewerThanLastChangedTimestamp(entry.timestamp)

    config.syncLogger.logInfo(s"needsWriting for entry at ${entry.timestamp}--> lastCacheUpdate: ${lastCacheUpdate} (newer: ${isEntryNewerThanCache}), lastKnown: ${curSyncStatus.lastKnownRemoteValue.map(_.timestamp)} (newer: ${isEntryNewerThanLastKnown})")

    isEntryNewerThanLastKnown || isEntryNewerThanCache
  }

  def chooseNewerTimestamp(other: LocalDateTime): Option[LocalDateTime] = {
    Some(lastCacheUpdate.filter(_.isAfter(other)).getOrElse(other))
  }

  def addAll(set: Set[DataEntryToSync[K, D]], remoteTimestamp: LocalDateTime): RemoteSyncDataCache[K, D] = syncLock.synchronized {
    RemoteSyncDataCache(config, chooseNewerTimestamp(remoteTimestamp), latestEntries(knownEntries ++ set).values.toSet)
  }

  private def latestEntries(entries: Iterable[DataEntryToSync[K, D]]): Map[K, DataEntryToSync[K, D]] =
    entries.groupBy(_.dataKey).map { (key, versions) =>
      val newest = versions.maxBy(_.timestamp)
      require(!versions.exists(entry => entry.timestamp == newest.timestamp && entry != newest),
        s"Conflicting cache entries for key $key at ${newest.timestamp}")
      key -> newest
    }

  lazy val asMap: Map[K, DataEntryToSync[K, D]] = latestEntries(knownEntries)

  def getCurrentSyncStatus(key: K): SyncStatus[K, D] = asMap.get(key) match {
    case Some(entry) => SyncStatus(lastCacheUpdate, key, Some(entry))
    case None => SyncStatus(None, key, None)
  }

  def tryEnsureCacheIsAtLeastThisRecent(maxAge: LocalDateTime): Future[RemoteSyncDataCache[K, D]] = syncLock.synchronized {
    ensureCacheIsAtLeastThisRecent(maxAge).recover {
      case err: Throwable =>
        config.syncLogger.logExceptionWarn("Ignoring that ensureCacheIsAtLeastThisRecent failed because of", err)
        this
    }
  }

  def ensureCacheIsAtLeastThisRecent(maxAge: LocalDateTime): Future[RemoteSyncDataCache[K, D]] = syncLock.synchronized {
    lastCacheUpdate match {
      case Some(updatedAt) if !updatedAt.isBefore(maxAge) =>
        config.syncLogger.log(s"Cache is fresh enough (last request $updatedAt >= $maxAge)", INFO, Some(false))
        Future.successful(this)
      case _ =>
        config.syncLogger.log(s"Fetching cache (last request $lastCacheUpdate, required $maxAge)", INFO, Some(false))
        executeFetch()
    }
  }

  private def executeFetch(): Future[RemoteSyncDataCache[K, D]] =
    Try(config.reader.fetchAll(config.syncLogger)).fold(Future.failed, identity)
      .map(response => addAll(response.fetchedValues, response.timestampFetchResponse))

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
