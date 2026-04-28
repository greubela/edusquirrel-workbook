package it.evadid.core.datastructures.state

import it.evadid.core.datastructures.state.ObservableValue.DerivedObservableValue

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

trait ObservableValue[T] {

  protected val syncLock: ObservableValue[T] = this

  private[state] val derivedObserver: mutable.ListBuffer[Observer[T]] = mutable.ListBuffer()
  private[state] val observers: mutable.ListBuffer[Observer[T]] = mutable.ListBuffer()

  private[state] var lastPropagatedResult: Option[Try[T]] = None

  def addObserver(observer: Observer[T]): Subscription[T] = syncLock.synchronized {
    observers += observer
    lastPropagatedResult.foreach(result => forceNotifyObserver(result, observer))
    Subscription(this, observer)
  }

  private[state] def removeObserver(observer: Observer[T]): Unit = syncLock.synchronized {
    observers -= observer
    derivedObserver -= observer
  }

  def addObserver(handleOnUpdate: T => Any, handleOnError: Throwable => Any = _ => {}, informObserverWith: ExecutionMethod = ExecutionMethod.executeSync): Subscription[T] = syncLock.synchronized {
    addObserver(Observer(handleOnUpdate, handleOnError, informObserverWith))
  }

  def deriveValue[O](withFunc: T => O, executeFunctionWith: ExecutionMethod = ExecutionMethod.executeSync): ObservableValue[O] = syncLock.synchronized {
    val res = DerivedObservableValue(this, withFunc, executeFunctionWith)
    derivedObserver += Observer(res.handleOnBaseUpdate, res.handleOnBaseError, ExecutionMethod.executeSync)
    res
  }

  private[state] def forceNotifyObserver(calcResult: Try[T], curObserver: Observer[T]): Unit = syncLock.synchronized {
    calcResult match {
      case Success(value) => curObserver.executionMethod.handleExecution[T, Any](curObserver.handleOnUpdate, value, _ => {})
      case Failure(error) => curObserver.executionMethod.handleExecution[Throwable, Any](curObserver.handleOnError, error, _ => {})
    }
  }

  private[state] def notifyObservers(calcResult: Try[T], ignoreObservers: List[Observer[T]] = List()): Unit = syncLock.synchronized {
    def handleObserverList(observerList: List[Observer[T]]): Unit = observerList.foreach(curObserver => {
      if (!ignoreObservers.contains(curObserver)) forceNotifyObserver(calcResult, curObserver)
    })

    if (lastPropagatedResult.isEmpty || lastPropagatedResult.get != calcResult) {
      lastPropagatedResult = Some(calcResult)
      handleObserverList(derivedObserver.toList)
      handleObserverList(observers.toList)
    }
  }

}

object ObservableValue {

  case class DerivedObservableValue[I, O](baseState: ObservableValue[I], deriveFunc: I => O, executeDerivateFunctionWith: ExecutionMethod) extends ObservableValue[O] {
    private[state] def handleOnBaseUpdate(newBaseValue: I): Unit = syncLock.synchronized {
      executeDerivateFunctionWith.handleExecution(deriveFunc, newBaseValue, valueFinished => notifyObservers(valueFinished))
    }

    private[state] def handleOnBaseError(newBaseError: Throwable): Unit = {
      val throwException = new IllegalStateException("Derived Observable Value failed because of base value error", newBaseError)
      notifyObservers(Failure(throwException))
    }
  }

  case class CombinedObservableValue[A, B](baseA: ObservableValue[A], baseB: ObservableValue[B]) extends ObservableValue[(A, B)] {

    private var lastA: Option[Try[A]] = None
    private var lastB: Option[Try[B]] = None

    private def onUpdatedA(newValue: Try[A]): Unit = {
      lastA = Some(newValue)
      onValueChanged()
    }

    private def onUpdatedB(newValue: Try[B]): Unit = {
      lastB = Some(newValue)
      onValueChanged()
    }

    private def onValueChanged(): Unit = {
      if (lastA.isDefined && lastB.isDefined) {
        if (lastA.get.isFailure) {
          val throwException = new IllegalStateException("Combined Observable Value failed because of invalid A", lastA.get.failed.get)
          notifyObservers(Failure(throwException))
        } else if (lastB.get.isFailure) {
          val throwException = new IllegalStateException("Combined Observable Value failed because of invalid B", lastB.get.failed.get)
          notifyObservers(Failure(throwException))
        } else {
          notifyObservers(Success((lastA.get.get, lastB.get.get)))
        }
      }
    }

    baseA.derivedObserver += Observer(newValue => onUpdatedA(Success(newValue)), error => onUpdatedA(Failure(error)), ExecutionMethod.executeSync)
    baseB.derivedObserver += Observer(newValue => onUpdatedB(Success(newValue)), error => onUpdatedB(Failure(error)), ExecutionMethod.executeSync)
  }


}
