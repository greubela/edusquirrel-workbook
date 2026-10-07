package it.evadid.core.datastructures.state

import it.evadid.core.datastructures.state.observable.{ObservableValueImpl, ObserverDerivationLogic}
import munit.FunSuite

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
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
    future.map(value => assertEquals(value, 5))
  }

  test("DerivedObservableValue derives all values") {
    val base = State(1)
    val derived = base.observable.deriveValue(_ * 2, deriveLogic = ObserverDerivationLogic.DeriveAllValues)
    val seen = ListBuffer.empty[Int]
    derived.addObserver(v => seen += v)

    base.set(2)
    base.set(3)

    assertEquals(seen.toList, List(2, 4, 6))
    derived.currentValueOrWaitForUpdate.map(value => assertEquals(value, 6))
  }

  test("DerivedObservableValue with DeriveOnlyLastValues drops intermediate queued values") {
    val pending = scala.collection.mutable.Queue.empty[Runnable]
    val executionContext = new ExecutionContext {
      override def execute(runnable: Runnable): Unit = pending.enqueue(runnable)
      override def reportFailure(error: Throwable): Unit = throw error
    }
    val base = State(1)

    val derived = base.observable.deriveValue(
      withFunc = _ * 10,
      executeFunctionWith = ExecutionMethod.ExecuteLocalAsync(executionContext),
      deriveLogic = ObserverDerivationLogic.DeriveOnlyLastValues
    )

    val seen = ListBuffer.empty[Int]
    derived.addObserver(v => seen += v)

    base.set(2)
    base.set(3)
    base.set(4)
    assertEquals(seen.toList, Nil)
    while pending.nonEmpty do pending.dequeue().run()
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
    combined.currentValueOrWaitForUpdate.map(value => assertEquals(value, ("b", 2)))
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
