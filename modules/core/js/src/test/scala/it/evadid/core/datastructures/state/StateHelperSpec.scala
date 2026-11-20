package it.evadid.core.datastructures.state

import com.raquo.airstream.core.{Observer, Signal}
import com.raquo.airstream.ownership.ManualOwner
import com.raquo.airstream.state.Var
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.core.datastructures.state.async.AsyncDataState.{AsyncDataLoading, AsyncDataSuccess}
import it.evadid.core.datastructures.state.async.AsyncValue
import it.evadid.core.datastructures.state.observable.ObservableValueImpl
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableState}
import it.evadid.workbook.meta.ExecutionContextInformation
import it.evadid.util.JsDomSuite
import java.time.LocalDateTime
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success, Try}

class StateHelperSpec extends JsDomSuite {
  override protected def domGlobals: List[String] = Nil

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

  private def recording[T](signal: Signal[T], owner: ManualOwner): Var[Vector[Try[T]]] = {
    val seen = Var(Vector.empty[Try[T]])
    signal.addObserver(Observer.fromTry[T] { case value => seen.update(_ :+ value) })(using owner)
    seen
  }

  test("shared states and Airstream Vars mirror each other in both directions") {
    val source = Var("a")
    val window = StateHelper.fromAirstreamVarToState(source)
    window.set("b")
    assertEquals(source.now(), "b", "states created with the page-lifetime owner write through")
    val state = State(1)
    val store = state.toAirstreamVar
    state.set(2)
    assertEquals(store.now(), 2)
    store.set(3)
    assertEquals(state.now(), 3)
    state.update(_ * 10)
    assertEquals(store.now(), 30)
  }

  test("observable values become signals that reseed on start, report errors and stop observing when stopped") {
    val source = ObservableValueImpl[Int](None)
    val owner = new ManualOwner
    val options = recording(source.toOptionSignal(), owner)
    val withDefault = recording(source.toSignal(-1), owner)
    source.onNewValueArrived(Success(4))
    val failure = RuntimeException("lost")
    source.onNewValueArrived(Failure(failure))
    assertEquals(options.now().distinct, Vector(Success(None), Success(Some(4)), Failure(failure)))
    assertEquals(withDefault.now().distinct.take(2), Vector(Success(-1), Success(4)))
    val received = options.now().size
    owner.killSubscriptions()
    source.onNewValueArrived(Success(5))
    assertEquals(options.now().size, received, "a stopped signal no longer observes the value")
    val restarted = new ManualOwner
    try assertEquals(recording(source.toOptionSignal(), restarted).now().distinct, Vector(Success(Some(5))))
    finally restarted.killSubscriptions()
  }

  test("observable values become event streams of their updates and errors") {
    val source = ObservableValueImpl[String](Some("first"))
    val owner = new ManualOwner
    val seen = Var(Vector.empty[Try[String]])
    source.toEventStream().addObserver(Observer.fromTry[String] { case value => seen.update(_ :+ value) })(using owner)
    source.onNewValueArrived(Success("second"))
    val failure = RuntimeException("offline")
    source.onNewValueArrived(Failure(failure))
    owner.killSubscriptions()
    source.onNewValueArrived(Success("ignored"))
    assertEquals(seen.now(), Vector(Success("first"), Success("second"), Failure(failure)))
  }

  test("signals become observable values and asynchronous data") {
    val store = Var(2)
    val strict = store.signal.toObservableValue
    val derived = store.signal.map(_ * 10).toObservableValue
    assertEquals(strict.now(), Some(2))
    assertEquals(derived.now(), Some(20))
    store.set(3)
    assertEquals((strict.now(), derived.now()), (Some(3), Some(30)))
    assertEquals(store.signal.toAsync.stateNow(), AsyncDataSuccess(3))
    val computed = Promise[String]()
    val async = store.signal.mapAsync(value => computed.future.map(_ + value))
    computed.success("value ")
    waitUntil(async.stateNow() == AsyncDataSuccess("value 3"))
  }

  test("asynchronous data exposes every state as a strict signal") {
    val pending = Promise[Int]()
    val signal = it.evadid.core.datastructures.state.async.AsyncData.forFuture(pending.future).toStateSignal
    assert(signal.now().isInstanceOf[AsyncDataLoading[?, ?]])
    pending.success(8)
    assertEquals(AsyncValue(AsyncDataSuccess[Nothing, Int](1)).toStateSignal.now(), AsyncDataSuccess(1))
    waitUntil(signal.now() == AsyncDataSuccess(8))
  }

  test("interaction variables bind to Vars and signals that follow every history change") {
    val variable = InteractionVariable(TextInteraction("state-helper-answer"))
    val context = ExecutionContextInformation.default
    val bound = variable.createBoundVarWithUpdateImportance(UpdateImportance.MAJOR, context)
    val signal = variable.createInteractionSignal(context)
    assertEquals((bound.now(), signal.now()), ("", ""))
    bound.set("typed")
    assertEquals(variable.currentValue, "typed")
    assertEquals(variable.currentHistory.historyStates.lastStateOption.map(_.updateImportance), Some(UpdateImportance.MAJOR))
    assertEquals(signal.now(), "typed")
    variable.updateHistory(_.addState(InteractionVariableState("restored", UpdateImportance.MAJOR,
      LocalDateTime.now().plusSeconds(5))), context)
    assertEquals((bound.now(), signal.now()), ("restored", "restored"))
  }
}
