package it.evadid.core.datastructures.state

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.{Success, Try}

private[state] case class ObservableValueImpl[T](initValue: Option[T]) extends ObservableValue[T] {

  private var lastValuePropagated: Option[Try[T]] = initValue.map(Success(_))

  private val oneTimeObservers: mutable.ListBuffer[Observer[T]] = mutable.ListBuffer()
  private[state] val observers: mutable.ListBuffer[Observer[T]] = mutable.ListBuffer()

  private def allObserversSorted(): List[Observer[T]] = (oneTimeObservers.toList ++ observers.toList).sortBy(_.executionPriority).reverse

  private[state] def onNewValueArrived(newValueTry: Try[T]): Unit = syncLock.synchronized {
    if (lastValuePropagated.isEmpty || lastValuePropagated.get != newValueTry) {
      lastValuePropagated = Some(newValueTry)
      allObserversSorted().foreach(curObserver => fireObserver(newValueTry, curObserver))
      oneTimeObservers.clear()
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

  def addObserver(observer: Observer[T]): Subscription[T] = syncLock.synchronized {
    observers += observer
    lastValuePropagated.foreach(result => fireObserver(result, observer))
    Subscription(this, observer)
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
