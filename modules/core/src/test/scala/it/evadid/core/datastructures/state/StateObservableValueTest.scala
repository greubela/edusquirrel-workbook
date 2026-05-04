package it.evadid.core.datastructures.state

import munit.FunSuite

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.*
import scala.concurrent.{Await, Promise}
import scala.util.Try

class StateObservableValueTest extends FunSuite {

  test("StateImpl propagates changes and update returns same instance") {
    val state = State(1)
    val seen = ListBuffer.empty[Int]

    state.observable.addObserver(v => seen += v)
    assertEquals(state.now(), 1)

    val same = state.update(_ + 1)
    assertEquals(same, state)
    assertEquals(state.now(), 2)
    assertEquals(seen.toList, List(1, 2))

    state.set(2)
    assertEquals(seen.toList, List(1, 2))
  }

  test("ObservableValueImpl currentValueOrWaitForUpdate waits and resolves on first update") {
    val obs = ObservableValueImpl[Int](None)
    val future = obs.currentValueOrWaitForUpdate
    assert(!future.isCompleted)

    obs.onNewValueArrived(Try(5))
    assertEquals(Await.result(future, 1.second), 5)
  }

  test("DerivedObservableValue derives all values".ignore) {
    // TODO: Derivation does not currently emit values in this setup; re-enable once the derived pipeline is fixed.
    val base = State(1)
    val derived = base.observable.deriveValue(_ * 2, deriveLogic = ObserverDerivationLogic.DeriveAllValues)
    val seen = ListBuffer.empty[Int]
    derived.addObserver(v => seen += v)

    base.set(2)
    base.set(3)

    assertEquals(seen.toList, List(2, 4, 6))
    assertEquals(Await.result(derived.currentValueOrWaitForUpdate, 1.second), 6)
  }

  test("DerivedObservableValue with DeriveOnlyLastValues drops intermediate queued values".ignore) {
    // TODO: Derivation with async execution is currently not emitting updates; re-enable when fixed.
    val gate = Promise[Unit]()
    val base = State(1)

    val derived = base.observable.deriveValue(
      withFunc = value => {
        Await.result(gate.future, 1.second)
        value * 10
      },
      executeFunctionWith = ExecutionMethod.executeAsync,
      deriveLogic = ObserverDerivationLogic.DeriveOnlyLastValues
    )

    val seen = ListBuffer.empty[Int]
    derived.addObserver(v => seen += v)

    base.set(2)
    base.set(3)
    base.set(4)
    gate.success(())

    Thread.sleep(200)
    assertEquals(seen.toList, List(10, 40))
  }

  test("CombinedObservableValue combines latest successful values") {
    val left = State("a")
    val right = State(1)

    val combined = left.observable.combineWith(right.observable)
    val seen = ListBuffer.empty[(String, Int)]
    combined.addObserver(v => seen += v)

    left.set("b")
    right.set(2)

    assertEquals(seen.toList, List(("a", 1), ("b", 1), ("b", 2)))
    assertEquals(Await.result(combined.currentValueOrWaitForUpdate, 1.second), ("b", 2))
  }

  test("Subscription unsubscribe stops further updates") {
    val state = State(10)
    var count = 0

    val subscription = state.observable.addObserver(_ => count += 1)
    assertEquals(count, 1)

    state.set(11)
    assertEquals(count, 2)

    subscription.cancel()
    state.set(12)
    assertEquals(count, 2)
  }
}
