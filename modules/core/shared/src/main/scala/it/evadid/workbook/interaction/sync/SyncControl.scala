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

  def store[F <: InteractionFormat](interactionVariable: InteractionVariable[F], forceFlush: Boolean = false): Future[Unit]

  def fetchAndStore(forceFlush: Boolean = false): Future[Unit] = fetchAll(forceFlush).flatMap(_ => storeAll(forceFlush))

  def storeAndFetch(forceFlush: Boolean = false): Future[Unit] = storeAll(forceFlush).flatMap(_ => fetchAll(forceFlush))

  def ensureSynced(): Future[Unit] = fetchAndStore(true).flatMap(_ => fetchAll(true))

  def syncLogger: SyncLogger

}
