package it.evadid.core.datastructures.state.observable

import it.evadid.core.datastructures.state.*
import it.evadid.core.datastructures.state.async.{AsyncData, AsyncState}
import it.evadid.core.datastructures.state.async.AsyncDataState.AsyncDataSuccess

import scala.concurrent.{ExecutionContext, Future}
import scala.util.*

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

  def now(): Option[T]

  def toAsync: AsyncData[Nothing, T] = AsyncState(this.deriveValue(AsyncDataSuccess(_)))

  def addNextChangeObserver(observer: Observer[T]): Unit

  def addNextChangeObserver(handleOnUpdate: T => Any, handleOnError: Throwable => Any = _ => {}, informObserverWith: ExecutionMethod = ExecutionMethod.executeSync, observerPriority: Int = 0): Unit = syncLock.synchronized {
    def derivedFunc(newValueTry: Try[T]): Any = {
      newValueTry match {
        case Success(newValue) => handleOnUpdate(newValue)
        case Failure(error) => handleOnError(error)
      }
    }

    addNextChangeObserver(Observer(derivedFunc, informObserverWith, observerPriority))
  }

  private[state] def removeObserver(observer: Observer[T]): Unit

  def deriveAsync[O](withFunc: T => Future[O], executeFunctionWith: ExecutionMethod = ExecutionMethod.executeSync, deriveLogic: ObserverDerivationLogic = ObserverDerivationLogic.DeriveAllValues): ObservableValue[O] = {
    val res = ObservableValueImpl[O](None)

    def onNewBaseValueArrived(resTry: Try[T]): Unit = if (resTry.isSuccess) withFunc(resTry.get).onComplete(resTry => res.onNewValueArrived(resTry))(using ExecutionContext.global)

    now().foreach(curVal => onNewBaseValueArrived(Success(curVal)))
    addObserver(Observer(onNewBaseValueArrived, ExecutionMethod.executeSync, 10000))
    res
  }

  def deriveSome[O](withFunc: T => Option[O], executeFunctionWith: ExecutionMethod = ExecutionMethod.executeSync, deriveLogic: ObserverDerivationLogic = ObserverDerivationLogic.DeriveAllValues): ObservableValue[O] = {
    val res = ObservableValueImpl[O](None)

    def onNewBaseValueArrived(resTry: Try[T]): Unit = if (resTry.isSuccess) withFunc(resTry.get).foreach((newVal: O) => res.onNewValueArrived(Success(newVal)))

    now().foreach(curVal => onNewBaseValueArrived(Success(curVal)))
    addObserver(Observer(onNewBaseValueArrived, ExecutionMethod.executeSync, 10000))
    res
  }

  def deriveValue[O](withFunc: T => O, executeFunctionWith: ExecutionMethod = ExecutionMethod.executeSync, deriveLogic: ObserverDerivationLogic = ObserverDerivationLogic.DeriveOnlyLastValues): ObservableValue[O] = syncLock.synchronized {
    val res = DerivedObservableValue(withFunc, executeFunctionWith, deriveLogic)
    addObserver(Observer(res.handleOnNewBaseValue, ExecutionMethod.executeSync, 10000))
    res
  }

  def combineWith[O](other: ObservableValue[O]): ObservableValue[(T, O)] = syncLock.synchronized {
    val res = CombinedObservableValue(this, other)

    val obsOther = Observer[O](res.onUpdatedB, ExecutionMethod.executeSync, 10000)
    other.addObserver(obsOther)

    val obsThis = Observer[T](res.onUpdatedA, ExecutionMethod.executeSync, 10000)
    this.addObserver(obsThis)

    res
  }

}

object ObservableValue {


}