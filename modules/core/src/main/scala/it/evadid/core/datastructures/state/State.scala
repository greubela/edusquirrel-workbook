package it.evadid.core.datastructures.state

import scala.util.{Failure, Success, Try}

case class State[T](initValue: T) extends ObservableValue[T] {

  private var currentValue: T = {
    lastPropagatedResult = Some(Success(initValue))
    initValue
  }

  def now(): T = currentValue

  def set(newValue: T): Unit = syncLock.synchronized {
    if (currentValue != newValue) {
      currentValue = newValue
      notifyObservers(Success(newValue))
    }
  }

  def update(func: T => T, executionMethod: ExecutionMethod = ExecutionMethod.executeSync): Unit = syncLock.synchronized {
    executionMethod.handleExecution[T, T](func, currentValue, newValueTry => notifyObservers(newValueTry))
  }

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

}