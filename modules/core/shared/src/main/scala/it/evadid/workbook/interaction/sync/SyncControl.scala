package it.evadid.workbook.interaction.sync

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.interaction.variable.InteractionVariable
import it.evadid.workbook.meta.InteractionVariableSyncReport

import scala.concurrent.{ExecutionContext, Future}

trait SyncControl {

  private given ExecutionContext = ExecutionContext.global

  def storeAll(forceFlush: Boolean = false): Future[Unit]

  def fetchAll(forceFlush: Boolean = false): Future[Unit]

  def store[F <: InteractionFormat](interactionVariable: InteractionVariable[F], forceFlush: Boolean = false): Future[Unit]

  private def phases(first: => Future[Unit], second: => Future[Unit]): Future[Unit] = {
    def attempt(operation: => Future[Unit]) = scala.util.Try(operation).fold(Future.failed, identity)
    attempt(first).transformWith(before => attempt(second).transform(after => before.flatMap(_ => after)))
  }

  def fetchAndStore(forceFlush: Boolean = false): Future[Unit] = phases(fetchAll(forceFlush), storeAll(forceFlush))
  def storeAndFetch(forceFlush: Boolean = false): Future[Unit] = phases(storeAll(forceFlush), fetchAll(forceFlush))
  def ensureSynced(): Future[Unit] = fetchAndStore(true)

  def syncLogger: SyncLogger

}
