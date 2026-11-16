package it.evadid.workbook.interaction.sync

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.variable.InteractionVariable
import it.evadid.workbook.meta.InteractionVariableSyncReport

import scala.concurrent.{ExecutionContext, Future}

trait SyncControl {

  private given ExecutionContext = ExecutionContext.global

  def storeAll(forceFlush: Boolean = false): Future[Unit]

  def fetchAll(forceFlush: Boolean = false): Future[Unit]

  def store[T, GA, SA](interactionVariable: InteractionVariable[T, GA, SA], forceFlush: Boolean = false): Future[Unit]

  def fetchAndStore(forceFlush: Boolean = false): Future[Unit] = fetchAll(forceFlush).transformWith(_ => storeAll(forceFlush))

  def storeAndFetch(forceFlush: Boolean = false): Future[Unit] = storeAll(forceFlush).transformWith(_ => fetchAll(forceFlush))

  def ensureSynced(): Future[Unit] = fetchAll(true).transformWith(_ => storeAll(true)).transformWith(_ => fetchAll(true))

  def syncLogger: SyncLogger

}
