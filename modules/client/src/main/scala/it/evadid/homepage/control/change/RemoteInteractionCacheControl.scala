package it.evadid.homepage.control.change

import it.evadid.core.datastructures.state.StateHelper.RichSignal
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.storage.RemoteCacheController
import it.evadid.homepage.control.model.FullInfo
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.model.SyncStatus
import it.evadid.workbook.interaction.variable.InteractionVariable.InteractionVariableUntyped
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory}
import it.evadid.workbook.meta.InteractionVariableSyncReport

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

case class RemoteInteractionCacheControl(fullInfo: FullInfo) extends SyncControl {

  private given ExecutionContext = ExecutionContext.global

  private lazy val remoteCache: RemoteCacheController[SyncContext, String, SyncInformationWithContext] =
    new RemoteCacheController[SyncContext, String, SyncInformationWithContext](
      SyncLogger(fullInfo.loggerSystemInfo.syncCacheLogger),
      fullInfo.signals.currentSyncDestinationObservable
    ) {
      override def onCacheKeyChange(newKeys: List[SyncInformationWithContext], knownKeys: Set[SyncContext]): Future[?] = {
        println("[UGLY WARN] RemoteInteractionCacheControl:: nothing implemented yet for onCacheKeyChange!")
        Future.successful(())
      }
    }

  private def decodeStatuses[T, GA, SA](variable: InteractionVariable[T, GA, SA],
                                        statuses: Map[SyncInformationWithContext, SyncStatus[SyncContext, String]]) = {
    import it.evadid.workbook.interaction.sync.model.{DataEntryToSync, SyncStatus}
    statuses.map { (location, status) =>
      val decoded = status.lastKnownRemoteValue.flatMap { entry =>
        scala.util.Try(variable.serializerHistory.deserialize(entry.dataValue)).fold(
          error => {
            controlLogger.logExceptionWarn("Ignoring invalid history for " + entry.dataKey, error);
            None
          },
          history => Some(DataEntryToSync(entry.dataKey, history, entry.timestamp, entry.direction)))
      }
      location -> SyncStatus(status.lastCacheRequest, status.associatedKey, decoded)
    }
  }

  override def ensureCachesAreAtLeastThisRecent(maxAge: LocalDateTime): Future[Unit] = remoteCache.syncLock.synchronized {
    remoteCache.ensureMaxAgeSafe(maxAge).map(_ => ())
  }

  override def createCurrentReport[T, GA, SA](forVariable: InteractionVariable[T, GA, SA]): InteractionVariableSyncReport[T, GA, SA] = remoteCache.syncLock.synchronized {
    val syncContext = fullInfo.current.currentHomepageContext.toSyncContext(forVariable.keyForSerialization)
    val res = remoteCache.currentReport(syncContext)
    InteractionVariableSyncReport(forVariable.currentHistory, decodeStatuses(forVariable, res.cacheStatus), res.allAvailableCacheKeys)
  }

  override def createObservableReport[T, GA, SA](forVariable: InteractionVariable[T, GA, SA]): ObservableValue[InteractionVariableSyncReport[T, GA, SA]] = remoteCache.syncLock.synchronized {
    val observableContext: ObservableValue[UsageContext] = fullInfo.signals.currentUsageContext.toObservableValue
    val observableHistory: ObservableValue[InteractionVariableHistory[T, GA, SA]] = forVariable.observableHistoryWithDefault
    val observableCache = remoteCache.observableCache

    observableCache.combineWith(observableContext, observableHistory).deriveValue(tup => {
      val report = tup._1.createReportFor(tup._2.toSyncContext(forVariable.keyForSerialization))
      InteractionVariableSyncReport(tup._3, decodeStatuses(forVariable, report.cacheStatus), report.allAvailableCacheKeys)
    })
  }

  override def ensureCachesContainLastElementsToWrite(variables: List[InteractionVariableUntyped]): Future[Unit] = remoteCache.syncLock.synchronized {

    def shouldSyncUntil(syncInfo: SyncInformationWithContext): LocalDateTime = variables.flatMap(curVar => {
      val history = curVar.currentHistory.filterWithStrategy(syncInfo.syncInformation.syncStrategy)
      history.timestampLastChanged
    }).maxOption.getOrElse(LocalDateTime.now())

    remoteCache.requestCacheDependentUpdate(shouldSyncUntil).map(_ => ())
  }

  override def requestStore(from: List[InteractionVariableUntyped]): Future[Unit] = remoteCache.syncLock.synchronized {
    remoteCache.requestCacheDependentStore(curSyncInfo => {
      from.flatMap(curVariable => {
        curSyncInfo.dataToStore(curVariable)
      })
    }).map(_ => ())
  }

  override def controlLogger: it.evadid.util.logging.Logger = fullInfo.loggerSystemInfo.syncControlLogger

  override def syncLogger: SyncLogger = fullInfo.loggerSystemInfo.syncControlLogger

  private def load[T, GA, SA](intoVar: InteractionVariable[T, GA, SA]): Future[Unit] = Future {
    val histories = createCurrentReport(intoVar).curRemoteHistory.values.flatMap(_.lastKnownRemoteValue.map(_.dataValue)).toVector
    intoVar.updateHistory(_.mergeWith(histories), fullInfo.workbookContextInformation)
  }

  override def load(into: List[InteractionVariableUntyped]): Future[Unit] =
    Future.traverse(into)(curVar => load(curVar)).map(_ => ())

}
