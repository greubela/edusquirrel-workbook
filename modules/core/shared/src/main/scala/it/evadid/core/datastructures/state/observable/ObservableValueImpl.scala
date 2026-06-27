package it.evadid.core.datastructures.state.observable

import it.evadid.core.datastructures.state.*

import scala.collection.mutable
import scala.concurrent.*
import scala.util.*

private[core] case class ObservableValueImpl[T](initValue: Option[T]) extends ObservableValue[T] {

  private var lastValuePropagated: Option[Try[T]] = initValue.map(Success(_))

  protected val oneTimeObservers: mutable.ListBuffer[Observer[T]] = mutable.ListBuffer()
  protected val observers: mutable.ListBuffer[Observer[T]] = mutable.ListBuffer()

  protected def allObserversSorted(): List[Observer[T]] = (oneTimeObservers.toList ++ observers.toList).sortBy(_.executionPriority).reverse

  def now(): Option[T] = lastValuePropagated.flatMap(_.toOption)

  private[core] def onNewValueArrived(newValueTry: Try[T]): Unit = syncLock.synchronized {
    if (lastValuePropagated.isEmpty || lastValuePropagated.get != newValueTry) {
      //  println("ObservableValueImpl::onNewValueArrived: " + newValueTry + " (last: " + lastValuePropagated + ", observers: " + allObserversSorted().size + " = " + observers.size + " + " + oneTimeObservers.size + ")")
      lastValuePropagated = Some(newValueTry)
      allObserversSorted().foreach(curObserver => fireObserver(newValueTry, curObserver))
      oneTimeObservers.clear()
    } else {
      //  println("ObserableValueImpl::onNewValueArrived: suppressed update '" + newValueTry + "'!")
    }
  }

  private[state] def fireObserver(withValue: Try[T], observer: Observer[T]): Unit = syncLock.synchronized {
    observer.executionMethod.handleExecution[Try[T], Any](observer.handleOnUpdate, withValue, _ => {})
  }

  private[state] def removeObserver(observer: Observer[T]): Unit = syncLock.synchronized {
    observers -= observer
    oneTimeObservers -= observer
  }

  override def currentValueOrWaitForUpdate: Future[T] = syncLock.synchronized {
    if (lastValuePropagated.isDefined) {
      Future.fromTry(lastValuePropagated.get)
    } else {
      val promise: Promise[T] = Promise[T]()
      oneTimeObservers += new Observer(promise.complete, ExecutionMethod.executeSync, 0)
      promise.future
    }
  }

  def addNextChangeObserver(observer: Observer[T]): Unit = syncLock.synchronized {
    oneTimeObservers += observer
  }

  def addObserver(observer: Observer[T]): Subscription[T] = syncLock.synchronized {
    // println("observers added by: " + Exception().getStackTrace().mkString("->"))
    observers += observer
    lastValuePropagated.foreach(result => fireObserver(result, observer))
    Subscription(this, observer)
  }


}
