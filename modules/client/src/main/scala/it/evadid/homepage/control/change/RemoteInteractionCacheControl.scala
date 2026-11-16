package it.evadid.homepage.control.change

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.core.datastructures.storage.remote.RemoteDataSyncControllable
import it.evadid.homepage.control.model.FullInfo
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.WorkbookInteractionElementFull
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.variable.InteractionVariable
import it.evadid.workbook.meta.InteractionVariableSyncReport

import scala.concurrent.{ExecutionContext, Future}

case class RemoteInteractionCacheControl(fullInfo: FullInfo) extends SyncControl {

  private given ec: ExecutionContext = ExecutionContext.global

  val innerState: Var[RemoteDataSyncControllable] = {
    val res = Var(RemoteDataSyncControllable(fullInfo.loggerSystemInfo.syncControlLogger, fullInfo.signals.syncEnvironmentSignal.now()))

    // update cache with new context
    fullInfo.signals.syncEnvironmentSignal.foreach(newValue => fullInfo.synchronized {
      innerState.update(_.changeDestinations(newValue))
    })(using unsafeWindowOwner)

    //update vars with new fetched info

    val updateReportSignal = res.signal.flatMapSwitch(_.caches.updateReport.toSignal(Map()))
    updateReportSignal.foreach(valueMap => fullInfo.synchronized {
      valueMap.toList.foreach((curSyncContext, curJsonHistory) => {
        val infoNow = fullInfo.homepageInfoNow()
        if (curSyncContext.doesMatch(infoNow.toContext) && infoNow.workbookInfo.nonEmpty) {
          val interactionOp = infoNow.workbookInfo.get.loadedWorkbook.allContainedInteractionsById.values.find(_.interactionVariable.keyForSerialization == curSyncContext.keyForSerialisation)
          if (interactionOp.nonEmpty) {
            interactionOp.get.interactionVariable.addSerializedHistory(syncLogger, curJsonHistory, fullInfo.workbookContextInformation)
          }
        }
      })
    })(using unsafeWindowOwner)


    res
  }

  override def storeAll(forceFlush: Boolean): Future[Unit] = fullInfo.synchronized {
    innerState.now().storeAll(forceFlush)
  }

  override def fetchAll(forceFlush: Boolean): Future[Unit] = fullInfo.synchronized {
    innerState.now().fetchAll(forceFlush)
  }

  override def store[T, GA, SA](interactionVariable: InteractionVariable[T, GA, SA], forceFlush: Boolean): Future[Unit] = fullInfo.synchronized {
    innerState.now().store(interactionVariable, forceFlush)
  }

  override def syncLogger: SyncLogger = fullInfo.loggerSystemInfo.syncControlLogger


  def createReportFor[T, GA, SA](interaction: WorkbookInteractionElementFull[T, GA, SA]): Signal[Option[InteractionVariableSyncReport[?, ?, ?]]] = {
    val reportSignal = innerState.signal.flatMapSwitch(_.caches.reports.toOptionSignal())
    val historySignal = interaction.interactionVariable.historySignal
    innerState.signal.combineWith(reportSignal).combineWith(historySignal).map((curState, curReportOp, curHistory) => {
      if (curState.syncEnv.isEmpty || curReportOp.isEmpty) None else {
        val syncContext = curState.syncEnv.get.usageContext.toSyncContext(interaction.interactionVariable.keyForSerialization)
        val curReport = curReportOp.get
        if (!curReport.contains(syncContext)) None else {
          Some(InteractionVariableSyncReport[T, GA, SA](
            interaction.interactionVariable.serializerHistory,
            curHistory,
            curReport(syncContext),
            curState.caches.readWriters
          ))
        }
      }
    })

  }
}


/*
case class RemoteInteractionCacheControl(fullInfo: FullInfo) extends SyncControl {

  private given ExecutionContext = ExecutionContext.global

  private lazy val remoteCache: RemoteCacheController[SyncContext, String, SyncInformationWithContext] = {
    val controller = new RemoteCacheController[SyncContext, String, SyncInformationWithContext](
      SyncLogger(fullInfo.loggerSystemInfo.syncCacheLogger), fullInfo.signals.currentSyncDestinationObservable
    ) {
      override def onCacheKeyChange(newKeys: List[SyncInformationWithContext], knownKeys: Set[SyncContext]): Future[?] = Future.unit
    }
    // Restoration follows confirmed cache publications, including the automatic
    // fetch after login. It must not depend on a later navigation or reload.
    controller.observableCache.addObserver(restoreSnapshot)
    controller
  }

  private def mergeIfChanged[T, GA, SA](variable: InteractionVariable[T, GA, SA],
                                        histories: Vector[InteractionVariableHistory[T, GA, SA]]): Unit =
    if histories.nonEmpty && variable.currentHistory.mergeWith(histories) != variable.currentHistory then
      variable.updateHistory(_.mergeWith(histories), fullInfo.workbookContextInformation)

  private def restoreSnapshot(snapshot: RemoteCacheCollection[SyncContext, String, SyncInformationWithContext]): Unit = {
    val context = fullInfo.current.currentHomepageContext
    if resetStateVar.now() == SyncResetState.Idle && snapshot.remoteCaches.keys.forall(_.usageContext == context) then {
      fullInfo.current.allAvailableInteractions.foreach { interaction =>
        def restore[T, GA, SA](variable: InteractionVariable[T, GA, SA]): Unit = {
          val report = snapshot.createReportFor(context.toSyncContext(variable.keyForSerialization))
          val histories = decodeStatuses(variable, report.cacheStatus).values.flatMap(_.lastKnownRemoteValue.map(_.dataValue)).toVector
          mergeIfChanged(variable, histories)
        }

        restore(interaction.interactionVariable)
      }
    }
  }

  // Keep synchronization paused after a partial reset as well: automatically
  // writing the still-mounted answers would recreate records already deleted.
  private val resetStateVar = Var[SyncResetState](SyncResetState.Idle)
  override lazy val resetState: ObservableValue[SyncResetState] = resetStateVar.signal.toObservableValue

  override def resetAllData(): Future[Unit] = resetStateVar.now() match {
    case SyncResetState.Running(completion) => completion
    case _ =>
      val completion = Promise[Unit]()
      resetStateVar.set(SyncResetState.Running(completion.future))
      scala.util.Try(remoteCache.clearAllCaches { destination =>
        destination.handler.clearValues(destination.usageContext).map(_ => ())
      }).fold(Future.failed, identity).onComplete { result =>
        resetStateVar.set(result.fold(SyncResetState.Failed(_), _ => SyncResetState.Completed))
        completion.complete(result)
      }
      completion.future
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
    if resetStateVar.now() != SyncResetState.Idle then Future.unit else remoteCache.ensureMaxAgeSafe(maxAge).map(_ => ())
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

    if resetStateVar.now() != SyncResetState.Idle then Future.unit else remoteCache.requestCacheDependentUpdate(shouldSyncUntil).map(_ => ())
  }

  override def requestStore(from: List[InteractionVariableUntyped]): Future[Unit] = remoteCache.syncLock.synchronized {
    if resetStateVar.now() != SyncResetState.Idle then Future.unit else remoteCache.requestCacheDependentStore(curSyncInfo => {
      from.flatMap(curVariable => {
        curSyncInfo.dataToStore(curVariable)
      })
    }).map(_ => ())
  }

  override def controlLogger: it.evadid.util.logging.Logger = fullInfo.loggerSystemInfo.syncControlLogger

  override def syncLogger: SyncLogger = fullInfo.loggerSystemInfo.syncControlLogger

  private def load[T, GA, SA](intoVar: InteractionVariable[T, GA, SA], context: UsageContext): Future[Unit] = Future {
    if resetStateVar.now() == SyncResetState.Idle && fullInfo.current.currentHomepageContext == context then {
      val histories = createCurrentReport(intoVar).curRemoteHistory.values.flatMap(_.lastKnownRemoteValue.map(_.dataValue)).toVector
      mergeIfChanged(intoVar, histories)
    }
  }

  override def load(into: List[InteractionVariableUntyped]): Future[Unit] = {
    val context = fullInfo.current.currentHomepageContext
    Future.traverse(into)(curVar => load(curVar, context)).map(_ => ())
  }

}*/
