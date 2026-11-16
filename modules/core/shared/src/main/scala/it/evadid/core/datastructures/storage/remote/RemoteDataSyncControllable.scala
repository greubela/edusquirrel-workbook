package it.evadid.core.datastructures.storage.remote

import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.WorkbookInteractionUntyped
import it.evadid.workbook.interaction.sync.{SyncContext, SyncControl, SyncInformationWithContext}
import it.evadid.workbook.interaction.variable.InteractionVariable

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

object RemoteDataSyncControllable {
  def apply(syncLogger: SyncLogger, syncEnv: Option[WorkbookSyncEnvironment]): RemoteDataSyncControllable = {
    RemoteDataSyncControllable(syncEnv, RemoteDataSynchronisation(syncLogger, syncEnv.toSet.flatMap(_.syncDestinations)))
  }
}

case class RemoteDataSyncControllable(syncEnv: Option[WorkbookSyncEnvironment], caches: RemoteDataSynchronisation[SyncContext, String, SyncInformationWithContext]) extends SyncControl {

  lazy val allInteractionsMap: Map[SyncContext, WorkbookInteractionUntyped] = syncEnv.toSet.flatMap(curEnv => {
    curEnv.interactions.map(curInteraction => curEnv.usageContext.toSyncContext(curInteraction.interactionVariable.keyForSerialization) -> curInteraction)
  }).toMap


  private given ec: ExecutionContext = ExecutionContext.global

  def calcDataToSync(syncEnv: WorkbookSyncEnvironment): Map[SyncInformationWithContext, Map[SyncContext, String]] = {
    syncEnv.syncDestinations.map(curDestination => {
      val curData = syncEnv.interactions.map(curInteraction => {
        val syncContext = syncEnv.usageContext.toSyncContext(curInteraction.interactionVariable.keyForSerialization)
        syncContext -> curDestination.serialize(curInteraction.interactionVariable)
      }).toMap
      curDestination -> curData
    }).toMap
  }

  def storeAll(forceFlush: Boolean): Future[Unit] = {
    syncEnv.map(cur => calcDataToSync(cur)).match {
      case Some(toStoreMap) => {
        Future.traverse(toStoreMap.keySet)(curDest => {
          caches.requestStore(curDest, toStoreMap(curDest), LocalDateTime.now(), forceFlush)
        }).map(_ => ())
      }
      case None => Future.successful(())
    }
  }

  def changeDestinations(syncContextOp: Option[WorkbookSyncEnvironment]): RemoteDataSyncControllable = this.synchronized {
    ensureSynced()
    RemoteDataSyncControllable(syncLogger, syncContextOp)
  }

  override def fetchAll(forceFlush: Boolean): Future[Unit] = {
    caches.ensureMaxAge(allInteractionsMap.keySet, LocalDateTime.now(), forceFlush)
  }

  override def store[T, GA, SA](interactionVariable: InteractionVariable[T, GA, SA], forceFlush: Boolean): Future[Unit] = {
    val syncContext = syncEnv.toSet.map(_.usageContext.toSyncContext(interactionVariable.keyForSerialization))
    val storeMap: Map[SyncInformationWithContext, (SyncContext, String)] = syncEnv.toSet.map(_.usageContext.toSyncContext(interactionVariable.keyForSerialization)).flatMap(curSyncContext => {
      caches.readWriters.map(curRW => curRW -> (curSyncContext, curRW.serialize(interactionVariable)))
    }).toMap
    Future.traverse(storeMap.keySet)(curSyncContext => caches.requestStore(curSyncContext, List(storeMap(curSyncContext)).toMap, LocalDateTime.now(), forceFlush)).map(_ => ())
  }

  override def syncLogger: SyncLogger = syncLogger


}

