package it.evadid.core.datastructures.state

import munit.FunSuite

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import scala.concurrent.duration.*
import scala.concurrent.{Await, Promise}

class StateObservableValueJvmAsyncTest extends FunSuite {

  test("DerivedObservableValue currentValueOrWaitForUpdate resolves while async derivation is still running") {
    val gate = Promise[Unit]()
    val base = State(1)

    val derived = base.observable.deriveValue(
      withFunc = value => {
        Await.result(gate.future, 1.second)
        value * 100
      },
      executeFunctionWith = ExecutionMethod.executeAsync,
      deriveLogic = ObserverDerivationLogic.DeriveOnlyLastValues
    )

    base.set(2)

    val runningFuture = derived.currentValueOrWaitForUpdate
    assert(!runningFuture.isCompleted)

    gate.success(())
    assertEquals(Await.result(runningFuture, 1.second), 100)
    assertEquals(Await.result(derived.currentValueOrWaitForUpdate, 1.second), 200)
  }

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
}
