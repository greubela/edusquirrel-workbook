package it.evadid.core.datastructures.state

import it.evadid.core.datastructures.state.observable.ObservableValueImpl
import munit.FunSuite

import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.util.{Failure, Success, Try}

class StateBranchCoverageTest extends FunSuite {
  test("combineWith propagates failure branches") {
    val a = ObservableValueImpl[Int](None)
    val b = ObservableValueImpl[Int](None)
    val combined = a.combineWith(b)

    val errors = ListBuffer.empty[String]
    combined.addObserver(_ => (), e => errors += e.getMessage)

    a.onNewValueArrived(Failure(new RuntimeException("a")))
    b.onNewValueArrived(Success(1))
    assert(errors.last.contains("invalid A"))

    a.onNewValueArrived(Success(2))
    b.onNewValueArrived(Failure(new RuntimeException("b")))
    assert(errors.last.contains("invalid B"))

    a.onNewValueArrived(Failure(new RuntimeException("a2")))
    b.onNewValueArrived(Failure(new RuntimeException("b2")))
    assert(errors.last.contains("invalid A and B"))
  }

  test("observable addObserver dispatches success and failure") {
    val obs = ObservableValueImpl[Int](None)
    val seen = ListBuffer.empty[Int]
    val seenErr = ListBuffer.empty[String]
    obs.addObserver(v => seen += v, e => seenErr += e.getMessage)

    obs.onNewValueArrived(Success(3))
    obs.onNewValueArrived(Failure(new RuntimeException("boom")))

    assertEquals(seen.toList, List(3))
    assertEquals(seenErr.toList, List("boom"))
  }

  test("execution methods sync and async callbacks") {
    val sync = ExecutionMethod.executeSync
    var syncRes = 0
    sync.handleExecution[Int, Int](_ + 1, 4, {
      case Success(v) => syncRes = v
      case _ =>
    })
    assertEquals(syncRes, 5)

    val async = ExecutionMethod.executeAsync
    val p = scala.concurrent.Promise[Int]()
    async.handleExecution[Int, Int](_ + 2, 5, {
      case Success(v) => p.success(v)
      case Failure(e) => p.failure(e)
    })
    assertEquals(Await.result(p.future, 2.seconds), 7)
  }

  test("derived observable propagates base failures") {
    val base = ObservableValueImpl[Int](None)
    val derived = base.deriveValue(_ + 1)
    val errors = ListBuffer.empty[String]
    derived.addObserver(_ => (), e => errors += e.getMessage)

    base.onNewValueArrived(Failure(new RuntimeException("base-fail")))
    assert(errors.head.contains("base value error"))
  }
}
