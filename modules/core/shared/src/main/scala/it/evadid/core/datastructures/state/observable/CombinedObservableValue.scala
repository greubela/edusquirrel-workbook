package it.evadid.core.datastructures.state.observable

import it.evadid.core.datastructures.state.*

import scala.concurrent.Future
import scala.util.*


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
    lastA.zip(lastB).foreach((a, b) => outObs.onNewValueArrived(combineResults(a, b)))
  }

  private def combineResults(a: Try[A], b: Try[B]): Try[(A, B)] = (a, b) match {
    case (Success(valueA), Success(valueB)) => Success(valueA -> valueB)
    case (Failure(error), Failure(_)) =>
      Failure(new IllegalStateException("Combined Observable Value failed because of invalid A and B (cause reflects A)", error))
    case (Failure(error), _) =>
      Failure(new IllegalStateException("Combined Observable Value failed because of invalid A", error))
    case (_, Failure(error)) =>
      Failure(new IllegalStateException("Combined Observable Value failed because of invalid B", error))
  }

  override def currentValueOrWaitForUpdate: Future[(A, B)] = outObs.currentValueOrWaitForUpdate

  override def addObserver(observer: Observer[(A, B)]): Subscription[(A, B)] = outObs.addObserver(observer)

  override def deriveValue[O](withFunc: ((A, B)) => O, executeFunctionWith: ExecutionMethod, deriveLogic: ObserverDerivationLogic): ObservableValue[O] = outObs.deriveValue[O](withFunc, executeFunctionWith, deriveLogic)

  override def combineWith[O](other: ObservableValue[O]): ObservableValue[((A, B), O)] = outObs.combineWith(other)

  override private[state] def removeObserver(observer: Observer[(A, B)]): Unit = outObs.removeObserver(observer)

  override def addNextChangeObserver(observer: Observer[(A, B)]): Unit = outObs.addNextChangeObserver(observer)

  override def deriveAsync[O](withFunc: ((A, B)) => Future[O], executeFunctionWith: ExecutionMethod, deriveLogic: ObserverDerivationLogic): ObservableValue[O] = outObs.deriveAsync[O](withFunc, executeFunctionWith, deriveLogic)

  override def deriveSome[O](withFunct: ((A, B)) => Option[O], executeFunctionWith: ExecutionMethod, deriveLogic: ObserverDerivationLogic): ObservableValue[O] = outObs.deriveSome[O](withFunct, executeFunctionWith, deriveLogic)

  override def now(): Option[(A, B)] = outObs.now()
}