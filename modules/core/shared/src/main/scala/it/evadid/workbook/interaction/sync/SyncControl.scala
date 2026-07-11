package it.evadid.workbook.interaction.sync

import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.storage.RemoteSyncDataCache.SyncStatus
import it.evadid.core.util.io.Serializer
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.SyncControl.InteractionVariableSyncReport
import it.evadid.workbook.interaction.sync.SyncInformation.{SyncInformationWithContext, SyncSuccess}
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory, InteractionVariableHistorySerialized, InteractionVariableState}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

trait SyncControl {

  private given ExecutionContext = ExecutionContext.global

  def ensureCachesAreAtLeastThisRecent(maxAge: LocalDateTime): Future[?]

  def createObservableReport[T](forVariable: InteractionVariable[T]): ObservableValue[InteractionVariableSyncReport[T]]

  def createCurrentReport[T](forVariable: InteractionVariable[T]): InteractionVariableSyncReport[T]

  def requestStore(from: List[InteractionVariable[?]]): Future[?]

  def syncLogger: SyncLogger

  def updateAllCaches(): Future[?] = ensureCachesAreAtLeastThisRecent(LocalDateTime.now())

  def requestStore[T](from: InteractionVariable[T]): Future[?] = requestStore(List(from))


  def load(into: List[InteractionVariable[?]]): Future[?] = {
    into.foreach(_.executeLoad(this))
    Future.successful(())
  }

  def updateAndStoreAll(from: List[InteractionVariable[?]]): Future[?] = updateAllCaches().transformWith(_ => requestStore(from))

  def ensureFetchAndLoad(from: List[InteractionVariable[?]]): Future[?] = {
    updateAllCaches()
      .flatMap(_ => load(from))
  }

  def ensureAllStored(from: List[InteractionVariable[?]]): Future[?] = {
    ensureCachesContainLastElementsToWrite(from)
      .flatMap(_ => load(from))
      .flatMap(_ => requestStore(from))
  }

  def storeAndReset(from: List[InteractionVariable[?]]): Future[?] = {
    ensureAllStored(from)
      .map(_ => from.foreach(_.resetLocalHistory()))
  }

  def ensureCachesContainLastElementsToWrite(variables: List[InteractionVariable[?]]): Future[?]


}


object SyncControl {

  case class InteractionVariableSyncReport[T](serializer: Serializer[T], curLocalHistory: InteractionVariableHistory[T], curRemoteHistory: Map[SyncInformationWithContext, SyncStatus[SyncContext, InteractionVariableHistorySerialized]], allSyncLocations: List[SyncInformationWithContext]) {

    lazy val typedMap: Map[SyncInformationWithContext, InteractionVariableHistory[T]] = {
      curRemoteHistory.iterator.flatMap(remoteTup => {
        remoteTup._2.lastKnownRemoteValue.map(_.dataValue.deserializeIgnoreErrors(serializer)).map(his => remoteTup._1 -> his)
      }).toMap
    }

    lazy val lastRemoteStates: Map[SyncInformationWithContext, Option[InteractionVariableState[T]]] =
      curRemoteHistory.iterator.map(tup => tup._1 -> tup._2.lastKnownRemoteValue.flatMap(_.dataValue.lastStateOption).map(_.deserialize(serializer))).toMap

    lazy val latestSyncedAt: Map[SyncInformationWithContext, Boolean] =
      curRemoteHistory.map(tup => tup._1 -> {
        if (curLocalHistory.lastStateOption.isEmpty) true
        else if (tup._2.lastCacheRequest.isEmpty) false
        else !curLocalHistory.lastStateOption.get.timestamp.isAfter(tup._2.lastCacheRequest.get)
      }).toMap


    lazy val latestStateIsSyncedTo: Set[SyncInformationWithContext] = latestSyncedAt.filter(_._2).keySet
    lazy val latestStateIsNotSyncedTo: Set[SyncInformationWithContext] = curRemoteHistory.keys.filter(!latestStateIsSyncedTo.contains(_)).toSet

    lazy val allStatesEverywhere: Set[InteractionVariableState[T]] = curLocalHistory.events ++ allRemoteStates

    lazy val allRemoteStates: Set[InteractionVariableState[T]] = typedMap.flatMap(_._2.events).toSet
  }


}


