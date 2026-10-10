package it.evadid.core.datastructures.state.observable

import it.evadid.core.datastructures.state.ExecutionMethod
import munit.FunSuite
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Promise}
import scala.util.{Failure, Success}

class ObservableValueSpec extends FunSuite {
  private class QueuedContext extends ExecutionContext {
    private val tasks = scala.collection.mutable.Queue.empty[Runnable]
    def execute(task: Runnable): Unit = tasks.enqueue(task)
    def reportFailure(error: Throwable): Unit = throw error
    def drain(): Unit = while (tasks.nonEmpty) tasks.dequeue().run()
  }

  test("observers receive initial values and suppress equal updates") {
    val source = ObservableValueImpl(Some(1))
    val seen = ListBuffer.empty[Int]
    source.addObserver((n: Int) => seen += n)
    source.onNewValueArrived(Success(1))
    source.onNewValueArrived(Success(2))
    assertEquals(seen.toList, List(1, 2))
    assertEquals(source.now(), Some(2))
  }

  test("subscription cancellation is idempotent and leaves other observers active") {
    val source = ObservableValueImpl[Int](None)
    val seen = ListBuffer.empty[Int]
    val subscription = source.addObserver((n: Int) => seen += n)
    var other = 0
    source.addObserver((n: Int) => other = n)
    subscription.cancel()
    subscription.cancel()
    source.onNewValueArrived(Success(3))
    assertEquals(seen.toList, Nil)
    assertEquals(other, 3)
  }

  test("observers run in descending priority and exceptions do not stop delivery") {
    val source = ObservableValueImpl[Int](None)
    val seen = ListBuffer.empty[Int]
    source.addObserver((_: Int) => seen += 1, observerPriority = 1)
    source.addObserver((_: Int) => throw new IllegalStateException("listener"), observerPriority = 2)
    source.addObserver((_: Int) => seen += 3, observerPriority = 3)
    source.onNewValueArrived(Success(1))
    assertEquals(seen.toList, List(3, 1))
  }

  test("waiting futures complete on success or failure and replay the current result") {
    val source = ObservableValueImpl[Int](None)
    val first = source.currentValueOrWaitForUpdate
    val second = source.currentValueOrWaitForUpdate
    assert(!first.isCompleted)
    source.onNewValueArrived(Success(5))
    assertEquals(first.value, Some(Success(5)))
    assertEquals(second.value, Some(Success(5)))
    val error = new IllegalArgumentException("broken")
    source.onNewValueArrived(Failure(error))
    assertEquals(source.now(), None)
    assertEquals(source.currentValueOrWaitForUpdate.value, Some(Failure(error)))
  }

  test("one-time observers wait for a distinct update and run only once") {
    val source = ObservableValueImpl(Some(1))
    val seen = ListBuffer.empty[Int]
    source.addNextChangeObserver((n: Int) => seen += n)
    source.onNewValueArrived(Success(1))
    source.onNewValueArrived(Success(2))
    source.onNewValueArrived(Success(3))
    assertEquals(seen.toList, List(2))
  }

  test("a next-change observer registered inside a callback survives until the next update") {
    val source = ObservableValueImpl[Int](None)
    val seen = ListBuffer.empty[Int]
    source.addNextChangeObserver((n: Int) => {
      seen += n
      source.addNextChangeObserver((next: Int) => seen += next)
    })
    source.onNewValueArrived(Success(1))
    source.onNewValueArrived(Success(2))
    assertEquals(seen.toList, List(1, 2))
  }

  test("a reentrant update does not invoke an already-fired one-time observer again") {
    val source = ObservableValueImpl[Int](None)
    val seen = ListBuffer.empty[Int]
    source.addNextChangeObserver((n: Int) => {
      seen += n
      if (n == 1) source.onNewValueArrived(Success(2))
    })
    source.onNewValueArrived(Success(1))
    assertEquals(seen.toList, List(1))
  }

  test("constant observables expose, replay and derive their value") {
    val source = ConstantValueObservable(4)
    assertEquals(source.currentValueOrWaitForUpdate.value, Some(Success(4)))
    assertEquals(source.deriveValue(_ * 2).now(), Some(8))
    var next = 0
    source.addNextChangeObserver((n: Int) => next = n)
    assertEquals(next, 0)
  }

  test("empty observables return a failed future without throwing synchronously") {
    val empty = ConstantEmptyObservable[Int]()
    val waiting = empty.currentValueOrWaitForUpdate
    assert(waiting.value.exists(_.isFailure))
    assertEquals(empty.now(), None)
    assertEquals(empty.deriveValue(_ + 1).now(), None)
    assertEquals(empty.deriveSome(n => Some(n)).now(), None)
    assertEquals(empty.deriveAsync(n => scala.concurrent.Future.successful(n)).now(), None)
  }

  test("combined values wait for both inputs and update either side") {
    val a = ObservableValueImpl[Int](None)
    val b = ObservableValueImpl[String](None)
    val combined = a.combineWith(b)
    a.onNewValueArrived(Success(1))
    assertEquals(combined.now(), None)
    b.onNewValueArrived(Success("b"))
    assertEquals(combined.now(), Some((1, "b")))
    a.onNewValueArrived(Success(2))
    assertEquals(combined.now(), Some((2, "b")))
  }

  test("combined failures identify the failing side and recover") {
    val a = ObservableValueImpl(Some(1))
    val b = ObservableValueImpl(Some(2))
    val combined = a.combineWith(b)
    val error = new IllegalArgumentException("a")
    a.onNewValueArrived(Failure(error))
    val failure = combined.currentValueOrWaitForUpdate.value.get.failed.get
    assert(failure.getMessage.contains("invalid A"))
    assertEquals(failure.getCause, error)
    b.onNewValueArrived(Failure(new IllegalArgumentException("b")))
    assert(combined.currentValueOrWaitForUpdate.value.get.failed.get.getMessage.contains("A and B"))
    a.onNewValueArrived(Success(3))
    assert(combined.currentValueOrWaitForUpdate.value.get.failed.get.getMessage.contains("invalid B"))
    b.onNewValueArrived(Success(4))
    assertEquals(combined.now(), Some((3, 4)))
  }

  test("three and four inputs preserve tuple order") {
    val a = ConstantValueObservable(1)
    assertEquals(a.combineWith(ConstantValueObservable("b"), ConstantValueObservable(true)).now(), Some((1, "b", true)))
    assertEquals(a.combineWith(ConstantValueObservable("b"), ConstantValueObservable(true), ConstantValueObservable(4)).now(), Some((1, "b", true, 4)))
  }

  test("combined lists publish initial values, retain input order and update") {
    val a = ObservableValueImpl(Some(1))
    val b = ObservableValueImpl(Some(2))
    val combined = ObservableValue.fromList(List(a, b))
    assertEquals(combined.now(), Some(List(1, 2)))
    b.onNewValueArrived(Success(5))
    assertEquals(combined.now(), Some(List(1, 5)))
  }

  test("combined lists wait for missing inputs and report failed indices") {
    val a = ObservableValueImpl(Some(1))
    val b = ObservableValueImpl[Int](None)
    val combined = ObservableValue.fromList(List(a, b))
    assertEquals(combined.now(), None)
    val error = new IllegalArgumentException("b")
    b.onNewValueArrived(Failure(error))
    val failure = combined.currentValueOrWaitForUpdate.value.get.failed.get
    assert(failure.getMessage.contains("(1)"))
    assertEquals(failure.getCause, error)
    b.onNewValueArrived(Success(2))
    assertEquals(combined.now(), Some(List(1, 2)))
  }

  test("an empty list is immediately available") {
    assertEquals(ObservableValue.fromList[Int](Nil).now(), Some(Nil))
  }

  test("invalid combined-list indices are rejected without altering the result") {
    val combined = CombinedSequenceObservableValue(List(ConstantValueObservable(1)))
    combined.onUpdatedAtIndex(0, Success(1))
    intercept[IndexOutOfBoundsException](combined.onUpdatedAtIndex(-1, Success(2)))
    intercept[IndexOutOfBoundsException](combined.onUpdatedAtIndex(1, Success(2)))
    assertEquals(combined.now(), Some(List(1)))
  }

  test("synchronous derivation transforms updates, propagates failures and recovers") {
    val source = ObservableValueImpl(Some(1))
    val derived = source.deriveValue(n => if (n < 0) throw new IllegalArgumentException("negative") else n * 2)
    assertEquals(derived.now(), Some(2))
    source.onNewValueArrived(Success(-1))
    assert(derived.currentValueOrWaitForUpdate.value.get.isFailure)
    val error = new IllegalArgumentException("base")
    source.onNewValueArrived(Failure(error))
    assertEquals(derived.currentValueOrWaitForUpdate.value.get.failed.get.getCause, error)
    source.onNewValueArrived(Success(3))
    assertEquals(derived.now(), Some(6))
  }

  for ((policy, expected) <- List(ObserverDerivationLogic.DeriveAllValues -> List(1, 2, 3), ObserverDerivationLogic.DeriveOnlyLastValues -> List(1, 3))) {
    test(s"queued derivation respects $policy") {
      val context = new QueuedContext
      val source = ObservableValueImpl(Some(1))
      val seen = ListBuffer.empty[Int]
      val derived = source.deriveValue((n: Int) => { seen += n; n * 2 }, ExecutionMethod.ExecuteLocalAsync(context), policy)
      val pending = derived.currentValueOrWaitForUpdate
      source.onNewValueArrived(Success(2))
      source.onNewValueArrived(Success(3))
      context.drain()
      assertEquals(seen.toList, expected)
      assertEquals(pending.value, Some(Success(2)))
      assertEquals(derived.now(), Some(6))
    }
  }

  test("optional derivation evaluates the initial input once and keeps the last accepted value") {
    val source = ObservableValueImpl(Some(1))
    var calls = 0
    val derived = source.deriveSome(n => { calls += 1; if (n > 0) Some(n * 2) else None })
    assertEquals(calls, 1)
    source.onNewValueArrived(Success(0))
    assertEquals(derived.now(), Some(2))
    source.onNewValueArrived(Success(2))
    assertEquals(derived.now(), Some(4))
  }

  test("optional derivation propagates base and transformation failures then recovers") {
    val source = ObservableValueImpl(Some(1))
    val derived = source.deriveSome(n => if (n == 2) throw new IllegalArgumentException("transform") else Some(n))
    val error = new IllegalArgumentException("base")
    source.onNewValueArrived(Failure(error))
    assertEquals(derived.currentValueOrWaitForUpdate.value.get.failed.get.getCause, error)
    source.onNewValueArrived(Success(2))
    assertEquals(derived.currentValueOrWaitForUpdate.value.get.failed.get.getMessage, "transform")
    source.onNewValueArrived(Success(3))
    assertEquals(derived.now(), Some(3))
  }

  test("optional derivation honors the requested execution context") {
    val context = new QueuedContext
    val derived = ConstantValueObservable(1).deriveSome(n => Some(n * 2), ExecutionMethod.ExecuteLocalAsync(context))
    assertEquals(derived.now(), None)
    context.drain()
    assertEquals(derived.now(), Some(2))
  }

  for ((policy, expected) <- List(ObserverDerivationLogic.DeriveAllValues -> List(1, 2, 3), ObserverDerivationLogic.DeriveOnlyLastValues -> List(1, 3))) {
    test(s"future derivation serializes pending work and respects $policy") {
      val source = ObservableValueImpl(Some(1))
      val seen = ListBuffer.empty[Int]
      val promises = scala.collection.mutable.Map.empty[Int, Promise[Int]]
      val derived = source.deriveAsync(n => { seen += n; val p = Promise[Int](); promises(n) = p; p.future }, deriveLogic = policy)
      val pending = derived.currentValueOrWaitForUpdate
      source.onNewValueArrived(Success(2))
      source.onNewValueArrived(Success(3))
      assertEquals(seen.toList, List(1))
      promises(1).success(10)
      assertEquals(pending.value, Some(Success(10)))
      expected.tail.foreach(n => promises(n).success(n * 10))
      assertEquals(seen.toList, expected)
      assertEquals(derived.now(), Some(30))
    }
  }

  test("future derivation propagates failed futures, thrown functions and base failures") {
    val source = ObservableValueImpl(Some(1))
    val error = new IllegalArgumentException("future")
    val derived = source.deriveAsync(n => if (n == 1) scala.concurrent.Future.failed[Int](error) else if (n == 2) throw new IllegalArgumentException("function") else scala.concurrent.Future.successful(n))
    assertEquals(derived.currentValueOrWaitForUpdate.value, Some(Failure(error)))
    source.onNewValueArrived(Success(2))
    assertEquals(derived.currentValueOrWaitForUpdate.value.get.failed.get.getMessage, "function")
    source.onNewValueArrived(Failure(error))
    assertEquals(derived.currentValueOrWaitForUpdate.value.get.failed.get.getCause, error)
    source.onNewValueArrived(Success(3))
    assertEquals(derived.now(), Some(3))
  }

  test("future derivation invokes its function on the requested execution context") {
    val context = new QueuedContext
    var calls = 0
    val derived = ConstantValueObservable(2).deriveAsync(n => { calls += 1; scala.concurrent.Future.successful(n) }, ExecutionMethod.ExecuteLocalAsync(context))
    assertEquals(calls, 0)
    context.drain()
    assertEquals(calls, 1)
    assertEquals(derived.now(), Some(2))
  }

  for ((policy, expected) <- List(ObserverDerivationLogic.DeriveAllValues -> List(1, 2, 3), ObserverDerivationLogic.DeriveOnlyLastValues -> List(1, 3))) {
    test(s"optional derivation respects $policy while queued") {
      val context = new QueuedContext
      val source = ObservableValueImpl(Some(1))
      val seen = ListBuffer.empty[Int]
      val derived = source.deriveSome(n => { seen += n; Some(n) }, ExecutionMethod.ExecuteLocalAsync(context), policy)
      source.onNewValueArrived(Success(2))
      source.onNewValueArrived(Success(3))
      context.drain()
      assertEquals(seen.toList, expected)
      assertEquals(derived.now(), Some(3))
    }
  }

  test("optional and future derivations replay a pre-existing base failure") {
    val source = ObservableValueImpl[Int](None)
    val error = new IllegalArgumentException("existing failure")
    source.onNewValueArrived(Failure(error))
    val optional = source.deriveSome(n => Some(n))
    val asynchronous = source.deriveAsync(n => scala.concurrent.Future.successful(n))
    for (derived <- List(optional, asynchronous)) {
      assertEquals(derived.currentValueOrWaitForUpdate.value.get.failed.get.getCause, error)
    }
  }

  test("a failed future releases queued derivations for subsequent inputs") {
    val source = ObservableValueImpl(Some(1))
    val first = Promise[Int]()
    val second = Promise[Int]()
    val derived = source.deriveAsync(n => if (n == 1) first.future else second.future)
    val pending = derived.currentValueOrWaitForUpdate
    source.onNewValueArrived(Success(2))
    val error = new IllegalArgumentException("first")
    first.failure(error)
    assertEquals(pending.value, Some(Failure(error)))
    second.success(20)
    assertEquals(derived.now(), Some(20))
  }

}
