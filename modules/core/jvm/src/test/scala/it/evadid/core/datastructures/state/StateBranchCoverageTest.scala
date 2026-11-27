package it.evadid.core.datastructures.state

import it.evadid.core.datastructures.state.observable.ObservableValueImpl
import munit.FunSuite

import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}

class StateBranchCoverageTest extends FunSuite {

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
}
