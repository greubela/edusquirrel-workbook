package it.evadid.core.datastructures.state.observable

import it.evadid.core.datastructures.state.{ExecutionMethod, Subscription}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case class CombinedSequenceObservableValue[A](observables: List[ObservableValue[A]]) extends ObservableValue[List[A]] {

  private var lastValues: List[Option[Try[A]]] = observables.map(_ => None)

  private val outObs: ObservableValueImpl[List[A]] = ObservableValueImpl[List[A]](None)

  private[state] def onUpdatedAtIndex(index: Int, newValue: Try[A]): Unit = syncLock.synchronized {
    if (index >= 0 || index < lastValues.size) {
      lastValues = lastValues.updated(index, Some(newValue))
    }
  }

  private[state] def onValueChanged(): Unit = syncLock.synchronized {
    val empty = lastValues.filter(_.isEmpty)
    if (empty.isEmpty) {
      val failedWithIndex = lastValues.zipWithIndex.filter(_._1.get.isFailure)
      val propagateValue: Try[List[A]] = if (failedWithIndex.nonEmpty) {
        val throwException = new IllegalStateException(s"Observable at positions ${failedWithIndex.map(_._2).mkString("(", ", ", ")")} contain failures (cause reflects ${failedWithIndex.head._2})", failedWithIndex.head._1.get.failed.get)
        Failure(throwException)
      } else {
        Success(lastValues.map(_.get.get))
      }
      outObs.onNewValueArrived(propagateValue)
    }
  }

  override def currentValueOrWaitForUpdate: Future[List[A]] = outObs.currentValueOrWaitForUpdate

  override def addObserver(observer: Observer[List[A]]): Subscription[List[A]] = outObs.addObserver(observer)

  override def deriveValue[O](withFunc: (List[A]) => O, executeFunctionWith: ExecutionMethod, deriveLogic: ObserverDerivationLogic): ObservableValue[O] = outObs.deriveValue[O](withFunc, executeFunctionWith, deriveLogic)

  override def combineWith[O](other: ObservableValue[O]): ObservableValue[(List[A], O)] = outObs.combineWith(other)

  override private[state] def removeObserver(observer: Observer[List[A]]): Unit = outObs.removeObserver(observer)

  override def addNextChangeObserver(observer: Observer[List[A]]): Unit = outObs.addNextChangeObserver(observer)

  override def deriveAsync[O](withFunc: List[A] => Future[O], executeFunctionWith: ExecutionMethod, deriveLogic: ObserverDerivationLogic): ObservableValue[O] = outObs.deriveAsync[O](withFunc, executeFunctionWith, deriveLogic)

  override def deriveSome[O](withFunct: List[A] => Option[O], executeFunctionWith: ExecutionMethod, deriveLogic: ObserverDerivationLogic): ObservableValue[O] = outObs.deriveSome[O](withFunct, executeFunctionWith, deriveLogic)

  override def now(): Option[List[A]] = outObs.now()

}
