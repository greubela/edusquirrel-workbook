package it.evadid.core.datastructures.state

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}


private case class DerivedObservableValue[I, O](
                                                 deriveFunc: I => O,
                                                 executeDerivateFunctionWith: ExecutionMethod,
                                                 deriveLogic: ObserverDerivationLogic
                                               ) extends ObservableValue[O] {

  private val inputQueue: mutable.Queue[Try[I]] = mutable.Queue()

  private var runningExecution: Option[Promise[O]] = None
  private val outObs: ObservableValueImpl[O] = ObservableValueImpl[O](None)

  private def ensureActivationRunning(): Unit = syncLock.synchronized {
    if (inputQueue.nonEmpty && runningExecution.isEmpty) {
      runningExecution = Some(Promise[O]())

      val startDerivationOn: Try[I] = deriveLogic.match {
        case ObserverDerivationLogic.DeriveOnlyLastValues => inputQueue.dequeueAll(_ => true).last
        case ObserverDerivationLogic.DeriveAllValues => inputQueue.dequeue()
      }

      startDerivationOn.match {
        case Success(baseValue) => {
          executeDerivateFunctionWith.handleExecution(deriveFunc, baseValue, finishedDerivation)
        }
        case Failure(error) => {
          val throwException = new IllegalStateException("Derived Observable Value failed because of base value error", error)
          finishedDerivation(Failure(throwException))
        }
      }
    }
  }

  private def finishedDerivation(derivedValue: Try[O]): Unit = syncLock.synchronized {
    if (runningExecution.nonEmpty) {
      runningExecution.get.complete(derivedValue)
      runningExecution = None
    }
    outObs.onNewValueArrived(derivedValue)
    ensureActivationRunning()
  }

  private[state] def handleOnNewBaseValue(newBaseValue: Try[I]): Unit = syncLock.synchronized {
    inputQueue.enqueue(newBaseValue)
    ensureActivationRunning()
  }

  override def currentValueOrWaitForUpdate: Future[O] = syncLock.synchronized {
    if (runningExecution.nonEmpty) runningExecution.get.future
    else outObs.currentValueOrWaitForUpdate
  }

  override def addObserver(observer: Observer[O]): Subscription[O] = syncLock.synchronized {
    outObs.addObserver(observer)
  }

  override def deriveValue[T](withFunc: O => T, executeFunctionWith: ExecutionMethod, deriveLogic: ObserverDerivationLogic): ObservableValue[T] = syncLock.synchronized {
    outObs.deriveValue[T](withFunc, executeFunctionWith, deriveLogic)
  }

  override def combineWith[T](other: ObservableValue[T]): ObservableValue[(O, T)] = outObs.combineWith[T](other)

  override private[state] def removeObserver(observer: Observer[O]): Unit = outObs.removeObserver(observer)
}