package it.evadid.workbook.meta

import it.evadid.workbook.interaction.sync.model.SyncStatus
import it.evadid.workbook.interaction.sync.{SyncContext, SyncInformationWithContext}
import it.evadid.workbook.interaction.variable.{InteractionVariableHistory, InteractionVariableState}

case class InteractionVariableSyncReport[T, GA, SA](
                                                     curLocalHistory: InteractionVariableHistory[T, GA, SA],
                                                     curRemoteHistory: Map[SyncInformationWithContext, SyncStatus[SyncContext, InteractionVariableHistory[T, GA, SA]]],
                                                     allSyncLocations: List[SyncInformationWithContext]
                                                   ) {

  lazy val lastRemoteStates: Map[SyncInformationWithContext, InteractionVariableState[T]] = {
    def getOldestFrom(status: SyncStatus[SyncContext, InteractionVariableHistory[T, GA, SA]]): Option[InteractionVariableState[T]] =
      status.lastKnownRemoteValue.flatMap(_.dataValue.historyStates.lastStateOption)

    curRemoteHistory.keySet.flatMap(curKey => getOldestFrom(curRemoteHistory(curKey)).map(curVal => curKey -> curVal)).toMap
  }

  lazy val latestSyncedAt: Map[SyncInformationWithContext, Boolean] =
    curRemoteHistory.map(tup => tup._1 -> {
      if (curLocalHistory.timestampLastChanged.isEmpty) true
      else if (tup._2.lastCacheRequest.isEmpty) false
      else !curLocalHistory.timestampLastChanged.get.isAfter(tup._2.lastCacheRequest.get)
    }).toMap


  lazy val latestStateIsSyncedTo: Set[SyncInformationWithContext] = latestSyncedAt.filter(_._2).keySet
  lazy val latestStateIsNotSyncedTo: Set[SyncInformationWithContext] = curRemoteHistory.keys.filter(!latestStateIsSyncedTo.contains(_)).toSet

  lazy val allInformationEverywhere: InteractionVariableHistory[T, GA, SA] = {
    val remoteHistories: Iterable[InteractionVariableHistory[T, GA, SA]] = curRemoteHistory.values.flatMap(_.lastKnownRemoteValue).map(_.dataValue)
    curLocalHistory.mergeWith(remoteHistories.toList)
  }

}

