package it.evadid.core.datastructures.state

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.async.AsyncDataState.*
import it.evadid.core.datastructures.state.async.{AsyncData, AsyncDataState}
import it.evadid.core.datastructures.state.observable.{ObservableValue, ObservableValueImpl}
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.interaction.sync.UpdateImportance
import it.evadid.workbook.interaction.sync.UpdateImportance.TEMPORARY
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory}
import it.evadid.workbook.interaction.binding.InteractionBinding

import scala.concurrent.{ExecutionContext, Future}
import com.raquo.airstream.state.{LazyDerivedVar, LazyStrictSignal}
import scala.util.{Failure, Success, Try}

object StateHelper {

  implicit class StateBasedVar[T](state: State[T]) {
    def toAirstreamVar: Var[T] = fromStateToAirstreamVar(state)

    def toSignal: Signal[T] = toAirstreamVar.signal
  }

  implicit class RichInteractionVariable[F <: InteractionFormat](underlying: InteractionVariable[F]) {

    /** The current binding's history; it follows a replaced binding. */
    def historySignal: Signal[InteractionVariableHistory[F]] = underlying.observableHistory.toSignal(underlying.currentHistory)

    def bindingSignal: Signal[InteractionBinding[F]] = underlying.bindingObservable.toSignal(underlying.binding)

  }

  implicit class RichObservableValue[T](underlying: ObservableValue[T]) {

    /** Safely exposes Option[T] since underlying.now() can be None. */
    def toOptionSignal(): Signal[Option[T]] = {
      val subscription = Var(Option.empty[it.evadid.core.datastructures.state.Subscription[T]])
      Signal.fromCustomSource[Option[T]](
        initial = Success(underlying.now()),
        start = (setCurrentValue, _, _, _) => {
          setCurrentValue(Success(underlying.now()))
          subscription.set(Some(underlying.addObserver(
            handleOnUpdate = value => setCurrentValue(Success(Some(value))),
            handleOnError = error => setCurrentValue(Failure(error))
          )))
        },
        stop = _ => {
          subscription.now().foreach(_.cancel())
          subscription.set(None)
        }
      )
    }

    /** Seed again on mount so an inactive signal never restores an old cache snapshot. */
    def toSignal(default: => T): Signal[T] = toOptionSignal().map(_.getOrElse(default))

    def toEventStream(): EventStream[T] = {
      // Explicitly reference your own package's Subscription to bypass Laminar's name collision
      val subscription = Var(Option.empty[it.evadid.core.datastructures.state.Subscription[T]])

      EventStream.fromCustomSource(
        shouldStart = _ => true,
        start = (fireValue, fireError, _, _) => {
          val sub = underlying.addObserver(
            // Pass values straight to the functions; Airstream handles transactions internally
            handleOnUpdate = v => fireValue(v),
            handleOnError = e => fireError(e)
          )
          subscription.set(Some(sub))
        },
        stop = _ => {
          subscription.now().foreach(_.cancel())
          subscription.set(None)
        }
      )
    }
  }

  implicit class RichSignal[T](underlying: Signal[T]) {

    /** Mirrors the signal for as long as `owner` lives. */
    def toObservableValue(using owner: Owner): ObservableValue[T] = {
      val res = underlying match {
        case strict: StrictSignal[? <: T] => ObservableValueImpl[T](Some(strict.now()))
        case _ => ObservableValueImpl[T](None)
      }
      underlying.foreach(next => res.onNewValueArrived(Success.apply(next)))
      res
    }

    def toAsync(using Owner): AsyncData[Nothing, T] = underlying.toObservableValue.toAsync

    def mapAsync[O](func: T => Future[O])(using ExecutionContext, Owner): AsyncData[Nothing, O] = toAsync.mapAsync(func)

  }


  implicit class RichAsyncData[F, S](underlying: AsyncData[F, S]) {

    /** Observes the data only while the signal has observers. */
    def toStateSignal: Signal[AsyncDataState[?, S]] =
      underlying.observeAllStates.toSignal(AsyncDataLoading[F, S]())

  }

  implicit class InteractionVariableOnJS[F <: InteractionFormat](interactionVariable: InteractionVariable[F]) {

    def createBoundVarWithUpdateImportance(updateImportance: UpdateImportance): Var[InteractionFormat.State[F]] =
      interactionVariable.createBoundStateWithUpdateImportance(updateImportance).toAirstreamVar


    /** The current answer; it observes the variable only while the signal has observers. */
    def createInteractionSignal: Signal[InteractionFormat.State[F]] =
      interactionVariable.observableValue.toSignal(interactionVariable.currentValue)

  }

  /** A Var backed by `state`: reads come from the state, writes go to it, and its signal observes the state only
    * while it has observers. Nothing outlives the elements that use the Var, so mounts need no explicit release.
    */
  def fromStateToAirstreamVar[T](state: State[T]): Var[T] =
    new LazyDerivedVar[Unit, T](Var(()), StateSignal(state), (unit, next) => { if state.now() != next then state.set(next); unit }, ".state")

  /** A lazily observing signal whose current value is always the state's value. */
  private final class StateSignal[T](state: State[T]) extends LazyStrictSignal[Option[T], T](
    state.observable.toOptionSignal(), _.getOrElse(state.now()), "State", ".signal") {
    override def tryNow(): Try[T] = Try(state.now())
  }

  /** Mirrors `airstreamVar` into a State for as long as `owner` lives. */
  def fromAirstreamVarToState[T](airstreamVar: Var[T], owner: Owner): State[T] = {
    val changes = ObservableValueImpl[T](Some(airstreamVar.now()))
    airstreamVar.signal.distinct.foreach(next => changes.onNewValueArrived(Success(next)))(using owner)
    State.fromStorage(() => airstreamVar.now(), airstreamVar.set, transform => airstreamVar.update(transform), changes)
  }
}
