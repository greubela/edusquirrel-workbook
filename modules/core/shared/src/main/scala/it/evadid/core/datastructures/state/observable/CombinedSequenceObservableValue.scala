package it.evadid.core.datastructures.state.observable

import it.evadid.core.datastructures.state.{ExecutionMethod, Subscription}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case class CombinedSequenceObservableValue[A](observables: List[ObservableValue[A]]) extends ObservableValue[List[A]] {

  private var lastValues: List[Option[Try[A]]] = observables.map(_ => None)

  private val outObs: ObservableValueImpl[List[A]] = ObservableValueImpl[List[A]](if (observables.isEmpty) Some(Nil) else None)

  private[state] def onUpdatedAtIndex(index: Int, newValue: Try[A]): Unit = syncLock.synchronized {
    if (index < 0 || index >= lastValues.size) throw new IndexOutOfBoundsException(s"Observable index $index outside ${lastValues.size} inputs")
    lastValues = lastValues.updated(index, Some(newValue))
    onValueChanged()
  }

  private[state] def onValueChanged(): Unit = syncLock.synchronized {
    combinedResult(lastValues).foreach(outObs.onNewValueArrived)
  }

  private def combinedResult(values: List[Option[Try[A]]]): Option[Try[List[A]]] =
    if values.exists(_.isEmpty) then None
    else {
      val results = values.flatten
      val failures = results.zipWithIndex.collect { case (Failure(error), index) => error -> index }
      Some(failures match {
        case (firstError, firstIndex) :: _ =>
          val positions = failures.map(_._2).mkString("(", ", ", ")")
          Failure(new IllegalStateException(s"Observable at positions $positions contain failures (cause reflects $firstIndex)", firstError))
        case Nil => Success(results.collect { case Success(value) => value })
      })
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
