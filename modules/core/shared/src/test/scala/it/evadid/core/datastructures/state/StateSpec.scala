package it.evadid.core.datastructures.state

import munit.FunSuite
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

class StateSpec extends FunSuite {
  test("state updates notify subscribers only when the value changes") {
    val state = State(1)
    val seen = ListBuffer.empty[Int]
    state.observable.addObserver((n: Int) => seen += n)
    state.set(1)
    assert(state.update(_ + 1) eq state)
    state.set(2)
    assertEquals(state.now(), 2)
    assertEquals(seen.toList, List(1, 2))
  }

  test("bidirectional states read through and write back to their base") {
    val base = State(3)
    val text = base.biMap[String](_.toString, _.toInt)
    val seen = ListBuffer.empty[String]
    text.observable.addObserver((s: String) => seen += s)
    assertEquals(text.now(), "3")
    text.set("4")
    base.set(5)
    assert(text.update(s => (s.toInt + 1).toString) eq text)
    assertEquals(base.now(), 6)
    assertEquals(text.now(), "6")
    assertEquals(seen.toList, List("3", "4", "5", "6"))
  }

  test("chained bidirectional states compose both mappings") {
    val base = State(2)
    val multiplied = base.biMap[Int](_ * 10, _ / 10)
    val text = multiplied.biMap[String](_.toString, _.toInt)
    text.set("70")
    assertEquals(base.now(), 7)
    assertEquals(multiplied.now(), 70)
    assertEquals(text.observable.now(), Some("70"))
  }

  test("a failing reverse mapping leaves the base unchanged") {
    val base = State(2)
    val text = base.biMap[String](_.toString, _.toInt)
    intercept[NumberFormatException](text.set("invalid"))
    assertEquals(base.now(), 2)
    assertEquals(text.now(), "2")
  }

  test("asynchronous updates change state on success and retain it on failure") {
    given ExecutionContext = ExecutionContext.global
    val state = State(2)
    state.updateAsyncUnsafe(n => Future.successful(n + 1))(ExecutionContext.global).flatMap { updated =>
      assert(updated eq state)
      assertEquals(state.now(), 3)
      state.updateAsyncUnsafe(_ => Future.failed(new IllegalArgumentException("rejected")))(ExecutionContext.global)
    }.map { updated =>
      assert(updated eq state)
      assertEquals(state.now(), 3)
    }
  }
}
