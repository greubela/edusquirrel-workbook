package it.evadid.core.datastructures.state

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


private case class CombinedObservableValue[A, B](baseA: ObservableValue[A], baseB: ObservableValue[B]) extends ObservableValue[(A, B)] {

  private var lastA: Option[Try[A]] = None
  private var lastB: Option[Try[B]] = None

  private val outObs: ObservableValueImpl[(A, B)] = ObservableValueImpl[(A, B)](None)

  private[state] def onUpdatedA(newValue: Try[A]): Unit = syncLock.synchronized {
    lastA = Some(newValue)
    onValueChanged()
  }

  private[state] def onUpdatedB(newValue: Try[B]): Unit = syncLock.synchronized {
    lastB = Some(newValue)
    onValueChanged()
  }

  private def onValueChanged(): Unit = syncLock.synchronized {
    if (lastA.isDefined && lastB.isDefined) {
      val propagateValue: Try[(A, B)] =
        if (lastA.get.isSuccess && lastB.get.isSuccess) {
          Success((lastA.get.get, lastB.get.get))
        } else if (lastA.get.isFailure && lastB.get.isFailure) {
          val throwException = new IllegalStateException("Combined Observable Value failed because of invalid A and B (cause reflects A)", lastA.get.failed.get)
          Failure(throwException)
        } else if (lastA.get.isFailure && lastB.get.isSuccess) {
          val throwException = new IllegalStateException("Combined Observable Value failed because of invalid A", lastA.get.failed.get)
          Failure(throwException)
        } else if (lastB.get.isFailure && lastA.get.isSuccess) {
          val throwException = new IllegalStateException("Combined Observable Value failed because of invalid B", lastB.get.failed.get)
          Failure(throwException)
        } else {
          ??? // unreachable
        }

      outObs.onNewValueArrived(propagateValue)
    }
  }

  override def currentValueOrWaitForUpdate: Future[(A, B)] = outObs.currentValueOrWaitForUpdate

  override def addObserver(observer: Observer[(A, B)]): Subscription[(A, B)] = outObs.addObserver(observer)

  override def deriveValue[O](withFunc: ((A, B)) => O, executeFunctionWith: ExecutionMethod, deriveLogic: ObserverDerivationLogic): ObservableValue[O] = outObs.deriveValue[O](withFunc, executeFunctionWith, deriveLogic)

  override def combineWith[O](other: ObservableValue[O]): ObservableValue[((A, B), O)] = outObs.combineWith(other)

  override private[state] def removeObserver(observer: Observer[(A, B)]): Unit = outObs.removeObserver(observer)
}