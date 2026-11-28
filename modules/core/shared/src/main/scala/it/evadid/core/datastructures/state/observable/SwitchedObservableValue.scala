package it.evadid.core.datastructures.state.observable

import it.evadid.core.datastructures.state.*

import scala.concurrent.Future
import scala.util.*

/** Publishes the values of the observable selected by the outer value; a replaced selection is unsubscribed, so it
  * can neither publish here nor be retained by this observable.
  */
private case class SwitchedObservableValue[A, B](select: A => ObservableValue[B]) extends ObservableValue[B] {

  private val output: ObservableValueImpl[B] = ObservableValueImpl[B](None)
  private var selected: Option[Subscription[B]] = None

  private[state] def handleOnOuterValue(outer: Try[A]): Unit = syncLock.synchronized {
    selected.foreach(_.cancel())
    selected = outer.toOption.map(value => select(value).addObserver(Observer[B](output.onNewValueArrived, ExecutionMethod.executeSync, 10000)))
    outer.failed.foreach(error => output.onNewValueArrived(Failure(error)))
  }

  override def currentValueOrWaitForUpdate: Future[B] = output.currentValueOrWaitForUpdate

  override def addObserver(observer: Observer[B]): Subscription[B] = output.addObserver(observer)

  override private[state] def removeObserver(observer: Observer[B]): Unit = output.removeObserver(observer)

  override def now(): Option[B] = output.now()

  override def addNextChangeObserver(observer: Observer[B]): Unit = output.addNextChangeObserver(observer)
}
