package it.evadid.workbook.interaction.sync.model

import it.evadid.core.datastructures.storage.RemoteSyncDataCache.*
import it.evadid.workbook.interaction.sync.model.DataEntryToSync.*

import java.time.LocalDateTime
import upickle.default.*

import it.evadid.core.util.io.serializer.DefaultSerializer.given
case class SyncStatus[K, V](lastCacheRequest: Option[LocalDateTime], associatedKey: K, lastKnownRemoteValue: Option[DataEntryToSync[K, V]]) derives ReadWriter {
  def isSubmittedTimeNewerThanLastRequest(timestamp: LocalDateTime): Boolean = if (lastCacheRequest.isEmpty) true else timestamp.isAfter(lastCacheRequest.get)

  def isSubmittedTimeNewerThanLastChangedTimestamp(timestamp: LocalDateTime): Boolean = if (lastKnownRemoteValue.isEmpty) true else timestamp.isAfter(lastKnownRemoteValue.get.timestamp)
}