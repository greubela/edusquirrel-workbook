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

  // A recent read is not a write acknowledgement. Check the data selected for each
  // destination, including grading and scaffolding, against its confirmed history.
  lazy val latestSyncedAt: Map[SyncInformationWithContext, Boolean] =
    allSyncLocations.map { location =>
      val expected = curLocalHistory.filterWithStrategy(location.syncInformation.syncStrategy)
      val confirmed = curRemoteHistory.get(location).flatMap(_.lastKnownRemoteValue).map(_.dataValue)
      location -> (expected.size == 0 || confirmed.exists(remote => remote.mergeWith(expected) == remote))
    }.toMap


  lazy val latestStateIsSyncedTo: Set[SyncInformationWithContext] = latestSyncedAt.filter(_._2).keySet
  lazy val latestStateIsNotSyncedTo: Set[SyncInformationWithContext] = allSyncLocations.filterNot(latestStateIsSyncedTo.contains).toSet

  lazy val allInformationEverywhere: InteractionVariableHistory[T, GA, SA] = {
    val remoteHistories: Iterable[InteractionVariableHistory[T, GA, SA]] = curRemoteHistory.values.flatMap(_.lastKnownRemoteValue).map(_.dataValue)
    curLocalHistory.mergeWith(remoteHistories.toList)
  }

}

