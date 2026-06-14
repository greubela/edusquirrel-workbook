package it.evadid.core.datastructures.state

import it.evadid.core.datastructures.state.ExecutionMethod.ExecuteLocalSync

import scala.util.{Failure, Success}

trait State[T] {

  def observable: ObservableValue[T]

  def now(): T

  def set(newValue: T): Unit

  def update(func: T => T): State[T]

  def biMap[O](mapForward: T => O, mapBackward: O => T, executionMethod: ExecutionMethod = ExecuteLocalSync()): State[O]
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

    def biMap[T](mapForward2: O => T, mapBackward2: T => O, executionMethod: ExecutionMethod = ExecuteLocalSync()): State[T] = baseState.observable.syncLock.synchronized {
      val res: State[T] = DerivedState[O, T](this, mapForward2, mapBackward2)
      res
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

    override def biMap[O](mapForward: T => O, mapBackward: O => T, executionMethod: ExecutionMethod): State[O] = observable.syncLock.synchronized {
      DerivedState(this, mapForward, mapBackward)
    }
  }


  /*

  def biMap[O](mapForward: T => O, mapBackward: O => T): State[O] = {
    val otherState = State(mapForward(currentValue))

    val backwardObserver: Observer[O] = Observer(
      newValue => notifyObservers(Success(mapBackward(newValue))),
      newError => notifyObservers(Failure(newError)),
      ExecutionMethod.executeSync)

    val forwardObserver = Observer[T](
      newValue => otherState.notifyObservers(Success(mapForward(newValue)), List(backwardObserver)),
      newError => otherState.notifyObservers(Failure(newError), List(backwardObserver)),
      ExecutionMethod.executeSync)

    // todo: would be nice if backward also does not trigger forward unless strictly necessary (update on backward triggers backward -> forward, even though forward would not be necessary)

    derivedObserver += forwardObserver
    otherState.derivedObserver += backwardObserver

    otherState
  }
   */

  /*def biMap[O](mapForward: T => O, mapBackward: O => T): State[O] = {
    val resForward = mapForward(currentValue)
    val otherState = State(resForward)

    val backwardObserver: Observer[O] = Observer(

      newValue => notifyObservers(Success(mapBackward(newValue))),
      newError => notifyObservers(Failure(newError)),
      ExecutionMethod.executeSync)

    val forwardObserver = Observer[T](
      newValue => otherState.notifyObservers(Success(mapForward(newValue)), List(backwardObserver)),
      newError => otherState.notifyObservers(Failure(newError), List(backwardObserver)),
      ExecutionMethod.executeSync)

    // todo: would be nice if backward also does not trigger forward unless strictly necessary (update on backward triggers backward -> forward, even though forward would not be necessary)

    derivedObserver += forwardObserver
    otherState.derivedObserver += backwardObserver

    otherState
  }*/

}