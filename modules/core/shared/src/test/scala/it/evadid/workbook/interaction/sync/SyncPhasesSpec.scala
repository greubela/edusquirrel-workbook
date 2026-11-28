package it.evadid.workbook.interaction.sync

import munit.FunSuite
import scala.concurrent.{ExecutionContext, Future, Promise}

class SyncPhasesSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global

  private class Phase {
    val started = Promise[Unit]()
    val finished = Promise[Unit]()
    def run(): Future[Unit] = { started.trySuccess(()); finished.future }
  }

  test("the second phase starts only after the first has finished") {
    val (read, write) = (Phase(), Phase())
    val result = SyncPhases.inOrder(read.run(), write.run())
    read.started.future.flatMap { _ =>
      assert(!write.started.isCompleted)
      read.finished.success(())
      write.started.future.flatMap { _ =>
        assert(!result.isCompleted)
        write.finished.success(())
        result
      }
    }
  }

  test("a failed first phase still runs the second and reports the first failure") {
    val (read, write) = (Phase(), Phase())
    val failure = IllegalStateException("offline")
    val result = SyncPhases.inOrder(read.run(), write.run())
    read.finished.failure(failure)
    write.started.future.flatMap { _ =>
      assert(!result.isCompleted)
      write.finished.success(())
      result.failed.map(error => assert(error eq failure))
    }
  }

  test("a thrown phase counts as a failure, and settled reports the first failure after all settle") {
    val failure = IllegalStateException("write failed")
    val pending = Promise[Unit]()
    val settled = SyncPhases.settled(Seq(Future.failed(failure), pending.future))
    assert(!settled.isCompleted)
    pending.success(())
    SyncPhases.inOrder(throw failure, Future.unit).failed.map(error => assert(error eq failure))
      .flatMap(_ => settled.failed).map(error => assert(error eq failure))
  }
}
