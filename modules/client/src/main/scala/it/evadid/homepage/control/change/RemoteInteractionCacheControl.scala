package it.evadid.homepage.control.change

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.core.datastructures.storage.remote.RemoteDataSyncControllable
import it.evadid.core.datastructures.storage.remote.RemoteDataSynchronisation.CacheReportForKey
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
    fullInfo.signals.syncEnvironmentSignal.changes.foreach(newValue => fullInfo.synchronized {
      res.update(_.changeDestinations(newValue))
    })(using unsafeWindowOwner)

    //update vars with new fetched info

    val updateReportSignal = res.signal.flatMapSwitch(state => state.caches.updateReport.toSignal(Map()).map(values => (state, values)))
    updateReportSignal.foreach((origin, valueMap) => fullInfo.synchronized {
      valueMap.toList.foreach((curSyncContext, curJsonHistory) => {
        val infoNow = fullInfo.homepageInfoNow()
        if ((res.now() eq origin) && curSyncContext.doesMatch(infoNow.toContext) && infoNow.workbookInfo.nonEmpty) {
          val interactionOp = infoNow.workbookInfo.get.loadedWorkbook.allContainedInteractionsById.values.find(_.interactionVariable.keyForSerialization == curSyncContext.keyForSerialisation)
          if (interactionOp.nonEmpty) {
            restore(interactionOp.get.interactionVariable, curJsonHistory)
          }
        }
      })
    })(using unsafeWindowOwner)


    res
  }

  private def restore[T, GA, SA](variable: InteractionVariable[T, GA, SA], json: String): Unit =
    variable.serializerHistory.tryDeserialize(json) match {
      case Left(error) => syncLogger.logExceptionWarn(s"Ignoring invalid history for ${variable.keyForSerialization}", error)
      case Right(history) =>
        val merged = variable.currentHistory.mergeWith(history)
        if merged != variable.currentHistory then
          variable.updateHistory(_ => merged, fullInfo.workbookContextInformation)
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
        {
          Some(InteractionVariableSyncReport[T, GA, SA](
            interaction.interactionVariable.serializerHistory,
            curHistory,
            curReport.getOrElse(syncContext, CacheReportForKey(syncContext, Map.empty)),
            curState.caches.readWriters
          ))
        }
      }
    })

  }
}

