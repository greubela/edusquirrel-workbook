package it.evadid.workbook.meta

import it.evadid.core.datastructures.storage.remote.RemoteDataSynchronisation.CacheReportForKey
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.interaction.sync.{SyncContext, SyncInformationWithContext}
import it.evadid.workbook.interaction.variable.{InteractionVariableHistory, InteractionVariableState}
import upickle.default.*


case class InteractionVariableSyncReport[T, GA, SA]
(
  serializerHistory: Serializer[InteractionVariableHistory[T, GA, SA]],
  curLocalHistory: InteractionVariableHistory[T, GA, SA],
  // curRemoteHistory: Map[SyncInformationWithContext, RemoteValue[SyncContext, String]],
  cacheReport: CacheReportForKey[SyncContext, String, SyncInformationWithContext],
  allSyncLocations: Set[SyncInformationWithContext]
) {

  lazy val cacheHistoryMap: Map[SyncInformationWithContext, InteractionVariableHistory[T, GA, SA]] = {
    {
      cacheReport.lastKnownValues.toList.flatMap((curSyncInfo, curRemoteValue) => {
        serializerHistory.tryDeserialize(curRemoteValue.value).match {
          case Left(err) => None
          case Right(value) => Some(curSyncInfo -> value)
        }
      }).toMap
    }
  }

  lazy val cacheLatestStateMap: Map[SyncInformationWithContext, InteractionVariableState[T]] = {
    cacheHistoryMap.toList.flatMap((curSyncInfo, curHistory) => curHistory.historyStates.lastStateOption.match {
    case Some(lastState) => Some(curSyncInfo -> lastState)
    case None => None
    }).toMap
  }

  def destinationsWithSufficientWrites(): Set[SyncInformationWithContext] = {
    cacheHistoryMap.toSet.filter((curSyncDest, curRemoteHistory) => {
      !(curRemoteHistory.timestampLastChanged.isEmpty || curRemoteHistory.timestampLastChanged.get.isBefore(curLocalHistory.timestampLastChanged.get))
    }).map(_._1)
  }

  lazy val allInformationEverywhere: InteractionVariableHistory[T, GA, SA] = {
    val allHistories = cacheHistoryMap.values.toList
    allHistories.fold(curLocalHistory)(_.mergeWith(_))
  }

}

