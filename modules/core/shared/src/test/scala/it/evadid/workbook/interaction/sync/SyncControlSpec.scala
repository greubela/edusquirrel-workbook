package it.evadid.workbook.interaction.sync

import it.evadid.util.logging.BasicLogger
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.interaction.variable.InteractionVariable
import munit.FunSuite
import scala.concurrent.{ExecutionContext, Future, Promise}

class SyncControlSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private class ControlledSync extends SyncControl {
    val loadStarted = Promise[Unit]()
    val loadFinished = Promise[Unit]()
    val storeStarted = Promise[Unit]()
    val storeFinished = Promise[Unit]()
    val syncLogger = SyncLogger(BasicLogger())
    def fetchAll(forceFlush: Boolean): Future[Unit] = { loadStarted.trySuccess(()); loadFinished.future }
    def storeAll(forceFlush: Boolean): Future[Unit] = { storeStarted.trySuccess(()); storeFinished.future }
    def store[F <: InteractionFormat](value: InteractionVariable[F], forceFlush: Boolean): Future[Unit] = storeAll(forceFlush)
  }

  test("fetch and store waits for the read before submitting writes") {
    val sync = new ControlledSync
    val result = sync.fetchAndStore(true)
    sync.loadStarted.future.flatMap { _ =>
      assert(!sync.storeStarted.isCompleted)
      sync.loadFinished.success(())
      sync.storeStarted.future.flatMap { _ =>
        assert(!result.isCompleted)
        sync.storeFinished.success(())
        result
      }
    }
  }

  test("a failed read still attempts pending writes and reports the read failure") {
    val sync = new ControlledSync
    val failure = IllegalStateException("offline")
    val result = sync.ensureSynced()
    sync.loadFinished.failure(failure)
    sync.storeStarted.future.flatMap { _ =>
      assert(!result.isCompleted)
      sync.storeFinished.success(())
      result.failed.map(error => assert(error eq failure))
    }
  }

  test("a failed store still fetches healthy destinations and preserves its failure") {
    val sync = new ControlledSync
    val failure = IllegalStateException("write failed")
    val result = sync.storeAndFetch(true)
    sync.storeFinished.failure(failure)
    sync.loadStarted.future.flatMap { _ =>
      sync.loadFinished.success(())
      result.failed.map(error => assert(error eq failure))
    }
  }
}
