package it.evadid.workbook.interaction.sync

import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.variable.InteractionVariable.InteractionVariableUntyped
import it.evadid.workbook.interaction.variable.InteractionVariable
import it.evadid.workbook.meta.InteractionVariableSyncReport

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

trait SyncControl {

  private given ExecutionContext = ExecutionContext.global

  def ensureCachesAreAtLeastThisRecent(maxAge: LocalDateTime): Future[Unit]

  def createObservableReport[T, GA, SA](forVariable: InteractionVariable[T, GA, SA]): ObservableValue[InteractionVariableSyncReport[T, GA, SA]]

  def createCurrentReport[T, GA, SA](forVariable: InteractionVariable[T, GA, SA]): InteractionVariableSyncReport[T, GA, SA]

  def requestStore(from: List[InteractionVariableUntyped]): Future[Unit]

  def syncLogger: SyncLogger

  def controlLogger: Logger

  def updateAllCaches(): Future[Unit] = ensureCachesAreAtLeastThisRecent(LocalDateTime.now())

  def requestStore[T, GA, SA](from: InteractionVariable[T, GA, SA]): Future[Unit] = requestStore(List(from))

  def load(into: List[InteractionVariableUntyped]): Future[Unit]

  def updateAndStoreAll(from: List[InteractionVariableUntyped]): Future[Unit] = updateAllCaches().transformWith(_ => requestStore(from))

  def ensureFetchAndLoad(from: List[InteractionVariableUntyped]): Future[Unit] = {
    updateAllCaches()
      .flatMap(_ => load(from))
  }

  def ensureAllStored(from: List[InteractionVariableUntyped]): Future[Unit] = {
    ensureCachesContainLastElementsToWrite(from)
      .flatMap(_ => load(from))
      .flatMap(_ => requestStore(from))
  }

  def storeAndReset(from: List[InteractionVariableUntyped]): Future[Unit] = {
    ensureAllStored(from)
      .map(_ => from.foreach(_.resetLocalHistory()))
  }

  def ensureCachesContainLastElementsToWrite(variables: List[InteractionVariableUntyped]): Future[Unit]

}
