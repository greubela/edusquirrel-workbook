package it.evadid.core.datastructures.storage.remote

import it.evadid.core.datastructures.storage.remote.RemoteDataSynchronisation.RemoteValue
import it.evadid.util.MapUtils

import java.time.LocalDateTime

case class RemoteCacheState[K, V](
                                   timestampLastFetchRequested: LocalDateTime,
                                   timestampLastStoreRequested: LocalDateTime,
                                   remoteValues: Map[K, RemoteValue[K, V]]
                                 ) {

  private lazy val valueTimestamps: Set[(K, V, LocalDateTime)] = remoteValues.toList.map(cur => (cur._1, cur._2.value, cur._2.timestampFetched)).toSet
  lazy val newestKnownValue: Option[(K, V, LocalDateTime)] = valueTimestamps.maxByOption(_._3)
  lazy val oldestKnownValue: Option[(K, V, LocalDateTime)] = valueTimestamps.minByOption(_._3)

  def newInformationReceived(newValues: Map[K, RemoteValue[K, V]]): RemoteCacheState[K, V] = {
    val mergedMaps = MapUtils.mergeMaps[K, RemoteValue[K, V]](remoteValues, newValues, (dupKey, v1, v2) => if (v1.timestampFetched.isAfter(v2.timestampFetched)) v1 else v2)
    RemoteCacheState(timestampLastFetchRequested, timestampLastStoreRequested, mergedMaps)
  }

  def newInformationReceived(key: K, remoteValue: RemoteValue[K, V]): RemoteCacheState[K, V] = {
    newInformationReceived(Map(key -> remoteValue))
  }

  def fetchRequested(timestamp: LocalDateTime): RemoteCacheState[K, V] = this.copy(timestampLastFetchRequested = timestamp)

  def storeRequested(timestamp: LocalDateTime): RemoteCacheState[K, V] = this.copy(timestampLastStoreRequested = timestamp)

}

object RemoteCacheState {

  def unknownState[K, V]: RemoteCacheState[K, V] = RemoteCacheState[K, V](
    LocalDateTime.of(1900, 1, 1, 0, 0, 0),
    LocalDateTime.of(1900, 1, 1, 0, 0, 0),
    Map()
  )

}