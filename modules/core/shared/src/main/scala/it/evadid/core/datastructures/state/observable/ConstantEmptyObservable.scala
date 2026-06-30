package it.evadid.core.datastructures.state.observable

import it.evadid.core.datastructures.state.{ExecutionMethod, Subscription}

import scala.concurrent.Future

case class ConstantEmptyObservable[T]() extends ObservableValue[T] {

  override def currentValueOrWaitForUpdate: Future[T] = Future.successful(throw new IllegalStateException("Empty Observable will never get a value!"))

  override def addObserver(observer: Observer[T]): Subscription[T] = {
    Subscription(this, observer)
  }

  override def now(): Option[T] = None

  override def addNextChangeObserver(observer: Observer[T]): Unit = {}

  override private[state] def removeObserver(observer: Observer[T]): Unit = {}

  override def deriveAsync[O](withFunc: T => Future[O], executeFunctionWith: ExecutionMethod, deriveLogic: ObserverDerivationLogic): ObservableValue[O] = this.asInstanceOf[ObservableValue[O]]

  override def deriveSome[O](withFunct: T => Option[O], executeFunctionWith: ExecutionMethod, deriveLogic: ObserverDerivationLogic): ObservableValue[O] = this.asInstanceOf[ObservableValue[O]]

  override def deriveValue[O](withFunc: T => O, executeFunctionWith: ExecutionMethod, deriveLogic: ObserverDerivationLogic): ObservableValue[O] = this.asInstanceOf[ObservableValue[O]]

  override def combineWith[O](other: ObservableValue[O]): ObservableValue[(T, O)] = CombinedObservableValue(this, other)
}
