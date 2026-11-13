package it.evadid.core.datastructures.state

import it.evadid.core.datastructures.state.observable.{ObservableValue, ObservableValueImpl, ObserverDerivationLogic}

import scala.util.Success

trait State[T] {

  def observable: ObservableValue[T]

  def now(): T

  def set(newValue: T): Unit

  def update(func: T => T): State[T]

  def biMap[O](mapForward: T => O, mapBackward: O => T): State[O]
}

object State {

  def apply[T](initVal: T): State[T] = StateImpl(initVal)

  private[state] case class DerivedState[I, O](baseState: State[I], mapForward: I => O, mapBackward: O => I) extends State[O] {

    lazy val observable: ObservableValue[O] = baseState.observable.deriveValue(mapForward, ExecutionMethod.executeSync, ObserverDerivationLogic.DeriveAllValues)

    def now(): O = baseState.observable.syncLock.synchronized {
      mapForward(baseState.now())
    }

    def set(newValue: O): Unit = baseState.observable.syncLock.synchronized {
      baseState.set(mapBackward(newValue))
    }

    def update(func: O => O): State[O] = baseState.observable.syncLock.synchronized {
      val res = func(now())
      set(res)
      this
    }

    def biMap[T](mapForward2: O => T, mapBackward2: T => O): State[T] = baseState.observable.syncLock.synchronized {
      DerivedState[I, T](baseState, mapForward.andThen(mapForward2), mapBackward2.andThen(mapBackward))
    }
  }

  private[state] case class StateImpl[T](initValue: T) extends State[T] {

    override val observable: ObservableValueImpl[T] = ObservableValueImpl[T](Some(initValue))

    private var currentValue: T = initValue

    def now(): T = observable.syncLock.synchronized {
      currentValue
    }

    def set(newValue: T): Unit = observable.syncLock.synchronized {
      if (currentValue != newValue) {
        currentValue = newValue
        observable.onNewValueArrived(Success(newValue))
      }
    }

    def update(func: T => T): State[T] = observable.syncLock.synchronized {
      val res = func(currentValue)
      set(res)
      this
    }

    override def biMap[O](mapForward: T => O, mapBackward: O => T): State[O] = observable.syncLock.synchronized {
      DerivedState(this, mapForward, mapBackward)
    }
  }



}
