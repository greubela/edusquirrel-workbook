package it.evadid.core.datastructures.state

import com.raquo.airstream.ownership.ManualOwner
import com.raquo.airstream.state.Var
import munit.FunSuite

class StateHelperSpec extends FunSuite {
  test("Var-backed state observes external edits and writes to the original store") {
    val owner = new ManualOwner
    try {
      val store = Var(1)
      val state = StateHelper.fromAirstreamVarToState(store, owner)
      val received = State(Vector.empty[Int])
      state.observable.addObserver(value => received.update(_ :+ value))
      state.update(_ + 1)
      assertEquals(store.now(), 2)
      store.set(3)
      assertEquals(state.now(), 3)
      assertEquals(received.now(), Vector(1, 2, 3))
      val text = state.biMap(_.toString, _.toInt)
      text.set("4")
      assertEquals(store.now(), 4)
      store.set(5)
      assertEquals(text.now(), "5")
    } finally owner.killSubscriptions()
  }

  test("updates queued in one Airstream transaction compose without overwriting each other") {
    val owner = new ManualOwner
    try {
      val store = Var(1)
      val trigger = Var(0)
      val state = StateHelper.fromAirstreamVarToState(store, owner)
      trigger.signal.changes.foreach { _ =>
        state.update(_ + 1)
        state.biMap(_.toString, _.toInt).update(text => (text.toInt + 1).toString)
      }(using owner)
      trigger.set(1)
      assertEquals(store.now(), 3)
      assertEquals(state.now(), 3)
    } finally owner.killSubscriptions()
  }

  test("detaching observers preserves the state handle for pending operations") {
    val owner = new ManualOwner
    val store = Var(1)
    val state = StateHelper.fromAirstreamVarToState(store, owner)
    val received = State(Vector.empty[Int])
    state.observable.addObserver(value => received.update(_ :+ value))
    owner.killSubscriptions()
    store.set(2)
    state.update(_ + 1)
    assertEquals(store.now(), 3)
    assertEquals(state.now(), 3)
    assertEquals(received.now(), Vector(1))
  }
}
