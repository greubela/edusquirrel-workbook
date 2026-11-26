package it.evadid.core.datastructures.state.observable

import it.evadid.core.datastructures.state.{ExecutionMethod, Subscription}

import scala.concurrent.Future
import scala.util.Success

case class ConstantValueObservable[T](underlying: T) extends ObservableValue[T] {

  override def currentValueOrWaitForUpdate: Future[T] = Future.successful(underlying)

  override def addObserver(observer: Observer[T]): Subscription[T] = {
    observer.handleOnUpdate(Success(underlying))
    Subscription(this, observer)
  }

  override def now(): Option[T] = Some(underlying)

  override def addNextChangeObserver(observer: Observer[T]): Unit = {}

  override private[state] def removeObserver(observer: Observer[T]): Unit = {}


}
