package it.evadid.core.datastructures.state

import it.evadid.core.datastructures.state.observable.{ObservableValueImpl, ObserverDerivationLogic}
import it.evadid.util.logging.{Logger, LoggingLevel}
import it.evadid.workbook.interaction.sync.model.{DataEntryToSync, FetchResponse, SyncDirection}
import munit.FunSuite

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

class StateObservableValueJvmAsyncTest extends FunSuite {

  test("JVM async derivations can run in true parallel using dedicated thread pool") {
    val ec = scala.concurrent.ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))
    try {
      val exec = ExecutionMethod.ExecuteLocalAsync(ec)
      val started = new CountDownLatch(2)
      val release = new CountDownLatch(1)
      val inFlight = new AtomicInteger(0)
      val maxInFlight = new AtomicInteger(0)

      def trackParallelism(): Unit = {
        val now = inFlight.incrementAndGet()
        maxInFlight.updateAndGet(cur => math.max(cur, now))
        started.countDown()
        release.await(1, TimeUnit.SECONDS)
        inFlight.decrementAndGet()
      }

      val left = State(1).observable.deriveValue(
        withFunc = value => { trackParallelism(); value + 1 },
        executeFunctionWith = exec,
        deriveLogic = ObserverDerivationLogic.DeriveOnlyLastValues
      )

      val right = State(10).observable.deriveValue(
        withFunc = value => { trackParallelism(); value + 1 },
        executeFunctionWith = exec,
        deriveLogic = ObserverDerivationLogic.DeriveOnlyLastValues
      )

      assert(started.await(1, TimeUnit.SECONDS), "Both async derivations should start")
      release.countDown()

      assertEquals(Await.result(left.currentValueOrWaitForUpdate, 1.second), 2)
      assertEquals(Await.result(right.currentValueOrWaitForUpdate, 1.second), 11)
      assert(maxInFlight.get() >= 2, s"expected true parallelism, max in flight was ${maxInFlight.get()}")
    } finally {
      ec.shutdown()
      assert(ec.awaitTermination(2, TimeUnit.SECONDS))
    }
  }

  test("concurrent State updates and bidirectional view updates do not lose increments") {
    given ExecutionContext = ExecutionContext.global
    val count = State(0)
    val view = count.biMap(_.toString, _.toInt)
    Future.traverse(1 to 8) { worker => Future {
      (1 to 1000).foreach { _ =>
        if worker % 2 == 0 then count.update(_ + 1)
        else view.update(value => (value.toInt + 1).toString)
      }
    }}.map(_ => assertEquals(count.now(), 8000))
  }

}
