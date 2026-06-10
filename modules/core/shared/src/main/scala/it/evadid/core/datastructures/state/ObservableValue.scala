package it.evadid.core.datastructures.state

import it.evadid.core.datastructures.storage.AsyncData

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait ObservableValue[T] {

  private[state] val syncLock: ObservableValue[T] = this

  def currentValueOrWaitForUpdate: Future[T]

  def addObserver(observer: Observer[T]): Subscription[T]

  def addObserver(handleOnUpdate: T => Any, handleOnError: Throwable => Any = _ => {}, informObserverWith: ExecutionMethod = ExecutionMethod.executeSync, observerPriority: Int = 0): Subscription[T] = syncLock.synchronized {
    def derivedFunc(newValueTry: Try[T]): Any = {
      newValueTry match {
        case Success(newValue) => handleOnUpdate(newValue)
        case Failure(error) => handleOnError(error)
      }
    }

    addObserver(Observer(derivedFunc, informObserverWith, observerPriority))
  }

  private[state] def removeObserver(observer: Observer[T]): Unit

  def deriveValue[O](withFunc: T => O, executeFunctionWith: ExecutionMethod = ExecutionMethod.executeSync, deriveLogic: ObserverDerivationLogic = ObserverDerivationLogic.DeriveOnlyLastValues): ObservableValue[O]

  def combineWith[O](other: ObservableValue[O]): ObservableValue[(T, O)]
}

object ObservableValue {
  

  
  
}