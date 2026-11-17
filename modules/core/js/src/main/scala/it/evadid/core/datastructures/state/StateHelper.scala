package it.evadid.core.datastructures.state

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.async.AsyncDataState.*
import it.evadid.core.datastructures.state.async.{AsyncData, AsyncDataState}
import it.evadid.core.datastructures.state.observable.{ObservableValue, ObservableValueImpl}
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.interaction.sync.UpdateImportance
import it.evadid.workbook.interaction.sync.UpdateImportance.TEMPORARY
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory}
import it.evadid.workbook.meta.ExecutionContextInformation

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object StateHelper {

  implicit class StateBasedVar[T](state: State[T]) {
    def toAirstreamVar: Var[T] = fromStateToAirstreamVar(state)

    def toSignal: Signal[T] = toAirstreamVar.signal
  }

  implicit class VarBasedState[T](airstreamVar: Var[T]) {
    def toState: State[T] = fromAirstreamVarToState(airstreamVar)

    def toObservableValue: ObservableValue[T] = toState.observable
  }

  implicit class RichInteractionVariable[F <: InteractionFormat](underlying: InteractionVariable[F]) {

    def historySignal: Signal[InteractionVariableHistory[F]] = {
      val res: Signal[InteractionVariableHistory[F]] = underlying.observableHistoryWithDefault.toSignal(
        underlying.observableHistoryWithDefault.now().get
      )
      res
    }

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
      var subscription: Option[it.evadid.core.datastructures.state.Subscription[T]] = None

      EventStream.fromCustomSource(
        shouldStart = _ => true,
        start = (fireValue, fireError, _, _) => {
          val sub = underlying.addObserver(
            // Pass values straight to the functions; Airstream handles transactions internally
            handleOnUpdate = v => fireValue(v),
            handleOnError = e => fireError(e)
          )
          subscription = Some(sub)
        },
        stop = _ => {
          subscription.foreach(_.cancel())
          subscription = None
        }
      )
    }
  }

  implicit class RichSignal[T](underlying: Signal[T]) {

    def toObservableValue: ObservableValue[T] = {
      val res = underlying match {
        case strict: StrictSignal[T] => ObservableValueImpl[T](Some(strict.now()))
        case signal: Signal[T] => ObservableValueImpl[T](None)
      }
      underlying.foreach(next => res.onNewValueArrived(Success.apply(next)))(using unsafeWindowOwner)
      res
    }

    def toAsync: AsyncData[Nothing, T] = underlying.toObservableValue.toAsync

    def mapAsync[O](func: T => Future[O])(implicit ec: ExecutionContext): AsyncData[Nothing, O] = toAsync.mapAsync(func)

  }


  implicit class RichAsyncData[F, S](underlying: AsyncData[F, S]) {

    def toStateSignal: StrictSignal[AsyncDataState[?, S]] = {
      val res: Var[AsyncDataState[?, S]] = Var(AsyncDataLoading[Nothing, S]())
      underlying.observeAllStates.addObserver(onNext => res.set(onNext))
      res.signal
    }

  }

  implicit class ObservableValueAsync[T](underlying: ObservableValue[T]) {
    /*
    def toSignal: Signal[AsyncData[T]] = {
      val res: Var[AsyncData[T]] = Var(AsyncData.AsyncDataLoading[T]())
      underlying.addObserver(onNext => res.set(onNext))
      res.signal
    }

    def mapAsync[O](func: T => Future[O])(ec: ExecutionContext): ObservableValue[AsyncData[O]] = {
      val tmp: State[AsyncData[O]] = State(AsyncData.AsyncDataLoading[O]())
      underlying.addObserver(onNext => onNext.match {
      case AsyncData.AsyncDataLoading() => tmp.set(AsyncData.AsyncDataLoading[O]())
      case AsyncData.AsyncDataFailed(cause) => tmp.set(AsyncData.AsyncDataFailed[O](cause))
      case AsyncDataSuccess(dataValue) => func(dataValue).onComplete {
        case scala.util.Success(result) => tmp.set(AsyncDataSuccess(result))
        case scala.util.Failure(cause) => tmp.set(AsyncData.AsyncDataFailed[O](new Exception("Failure during ObservableValueAsync::mapAsync", cause)))
      }(using ec)
      })
      tmp.observable
    }

    def map[O](func: T => O): ObservableValue[AsyncData[O]] = {
      underlying.deriveValue(_.map(func))
    }*/


  }

  implicit class InteractionVariableOnJS[F <: InteractionFormat](interactionVariable: InteractionVariable[F]) {

    def createBoundVarWithUpdateImportance(updateImportance: UpdateImportance, context: ExecutionContextInformation): Var[F#InteractionStateType] = {

      val state = interactionVariable.createBoundStateWithUpdateImportance(updateImportance, context)
      fromStateToAirstreamVar(state)
    }

    def createInteractionSignal(context: ExecutionContextInformation): StrictSignal[F#InteractionStateType] = {
      // val res = Var[T](interactionVariable.currentValue)
      //interactionVariable.observableValue.addObserver(newValue => res.set(newValue))
      //res.signal

      createBoundVarWithUpdateImportance(TEMPORARY, context).signal
    }

  }

  def fromStateToAirstreamVar[T](state: State[T]): Var[T] = {
    val airstreamVar = Var(state.now())
    bindTogether(state, airstreamVar)
    airstreamVar
  }

  def fromAirstreamVarToState[T](airstreamVar: Var[T]): State[T] = {
    val state = State[T](airstreamVar.now())
    bindTogether(state, airstreamVar)
    state
  }

  def bindTogether[T](state: State[T], airstreamVar: Var[T]): Unit = {
    state.observable.addObserver(nextValue => if (airstreamVar.now() != nextValue) airstreamVar.set(nextValue))
    airstreamVar.signal.distinct.foreach(nextValue => if (state.now() != nextValue) state.set(nextValue))(using unsafeWindowOwner)
  }


}
