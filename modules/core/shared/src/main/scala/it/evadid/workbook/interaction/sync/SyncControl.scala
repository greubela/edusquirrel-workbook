package it.evadid.workbook.interaction.sync

import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.SyncControl.InteractionVariableSyncReport
import it.evadid.workbook.interaction.sync.model.SyncStatus
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory, InteractionVariableState, InteractionVariableUntyped}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

trait SyncControl {

  private given ExecutionContext = ExecutionContext.global

  def ensureCachesAreAtLeastThisRecent(maxAge: LocalDateTime): Future[?]

  def createObservableReport[T, GA, SA](forVariable: InteractionVariable[T, GA, SA]): ObservableValue[InteractionVariableSyncReport[T, GA, SA]]

  def createCurrentReport[T, GA, SA](forVariable: InteractionVariable[T, GA, SA]): InteractionVariableSyncReport[T, GA, SA]

  def requestStore(from: List[InteractionVariableUntyped]): Future[?]

  def syncLogger: SyncLogger

  def controlLogger: Logger

  def updateAllCaches(): Future[?] = ensureCachesAreAtLeastThisRecent(LocalDateTime.now())

  def requestStore[T, GA, SA](from: InteractionVariable[T, GA, SA]): Future[?] = requestStore(List(from))

  def load(into: List[InteractionVariableUntyped]): Future[?] = {
    into.foreach(_.executeLoad(this))
    Future.successful(())
  }

  def updateAndStoreAll(from: List[InteractionVariableUntyped]): Future[?] = updateAllCaches().transformWith(_ => requestStore(from))

  def ensureFetchAndLoad(from: List[InteractionVariableUntyped]): Future[?] = {
    updateAllCaches()
      .flatMap(_ => load(from))
  }

  def ensureAllStored(from: List[InteractionVariableUntyped]): Future[?] = {
    ensureCachesContainLastElementsToWrite(from)
      .flatMap(_ => load(from))
      .flatMap(_ => requestStore(from))
  }

  def storeAndReset(from: List[InteractionVariableUntyped]): Future[?] = {
    ensureAllStored(from)
      .map(_ => from.foreach(_.resetLocalHistory()))
  }

  def ensureCachesContainLastElementsToWrite(variables: List[InteractionVariableUntyped]): Future[?]

}


object SyncControl {

  case class InteractionVariableSyncReport[T, GA, SA](
                                               curLocalHistory: InteractionVariableHistory[T, GA, SA],
                                               curRemoteHistory: Map[SyncInformationWithContext, SyncStatus[SyncContext, InteractionVariableHistory[T, GA, SA]]],
                                               allSyncLocations: List[SyncInformationWithContext]
                                             ) {

    lazy val lastRemoteStates: Map[SyncInformationWithContext, InteractionVariableState[T]] = {
      def getOldestFrom(status: SyncStatus[SyncContext, InteractionVariableHistory[T, GA, SA]]): Option[InteractionVariableState[T]] =
        status.lastKnownRemoteValue.flatMap(_.dataValue.lastStateOption)

      curRemoteHistory.keySet.flatMap(curKey => getOldestFrom(curRemoteHistory(curKey)).map(curVal => curKey -> curVal)).toMap
    }

    lazy val latestSyncedAt: Map[SyncInformationWithContext, Boolean] =
      curRemoteHistory.map(tup => tup._1 -> {
        if (curLocalHistory.latestChangeTimestamp.isEmpty) true
        else if (tup._2.lastCacheRequest.isEmpty) false
        else !curLocalHistory.latestChangeTimestamp.get.isAfter(tup._2.lastCacheRequest.get)
      }).toMap


    lazy val latestStateIsSyncedTo: Set[SyncInformationWithContext] = latestSyncedAt.filter(_._2).keySet
    lazy val latestStateIsNotSyncedTo: Set[SyncInformationWithContext] = curRemoteHistory.keys.filter(!latestStateIsSyncedTo.contains(_)).toSet

    lazy val allStatesEverywhere: Set[InteractionVariableState[T]] = curLocalHistory.events ++ allRemoteStates

    lazy val allRemoteStates: Set[InteractionVariableState[T]] = curRemoteHistory.values.flatMap(_.lastKnownRemoteValue).flatMap(_.dataValue.events).toSet
  }


}


