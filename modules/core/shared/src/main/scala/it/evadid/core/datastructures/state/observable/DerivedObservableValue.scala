package it.evadid.core.datastructures.state.observable

import it.evadid.core.datastructures.state.*

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.*


private case class DerivedObservableValue[I, O](
                                                 deriveFunc: Either[I => O, I => Future[O]],
                                                 executeDerivateFunctionWith: ExecutionMethod,
                                                 deriveLogic: ObserverDerivationLogic
                                               ) extends ObservableValue[O] {

  private val inputQueue: mutable.Queue[Try[I]] = mutable.Queue()

  private var runningExecution: Option[Promise[O]] = None
  private val outObs: ObservableValueImpl[O] = ObservableValueImpl[O](None)

  private def takeNextInput(): Try[I] = deriveLogic match {
    case ObserverDerivationLogic.DeriveOnlyLastValues => inputQueue.dequeueAll(_ => true).last
    case ObserverDerivationLogic.DeriveAllValues => inputQueue.dequeue()
  }

  private def ensureActivationRunning(): Unit = syncLock.synchronized {
    if (inputQueue.nonEmpty && runningExecution.isEmpty) {
      runningExecution = Some(Promise[O]())
      runDerivation(takeNextInput())
    }
  }

  private def runDerivation(input: Try[I]): Unit = input match {
    case Failure(error) =>
      finishedDerivation(Failure(new IllegalStateException("Derived Observable Value failed because of base value error", error)))
    case Success(value) => deriveFunc match {
      case Left(function) =>
        executeDerivateFunctionWith.handleExecution(function, value, finishedDerivation)
      case Right(function) =>
        executeDerivateFunctionWith.handleExecution[I, Future[O]](function, value, {
          case Success(future) => future.onComplete(finishedDerivation)(using ExecutionContext.parasitic)
          case Failure(error) => finishedDerivation(Failure(error))
        })
    }
  }

  private def finishedDerivation(result: Try[O]): Unit = syncLock.synchronized {
    runningExecution.foreach(_.complete(result))
    runningExecution = None
    outObs.onNewValueArrived(result)
    ensureActivationRunning()
  }

  private[state] def handleOnNewBaseValue(newBaseValue: Try[I]): Unit = syncLock.synchronized {
    inputQueue.enqueue(newBaseValue)
    ensureActivationRunning()
  }

  override def currentValueOrWaitForUpdate: Future[O] = syncLock.synchronized {
    runningExecution.fold(outObs.currentValueOrWaitForUpdate)(_.future)
  }

  override def addObserver(observer: Observer[O]): Subscription[O] = syncLock.synchronized {
    outObs.addObserver(observer)
  }

  override private[state] def removeObserver(observer: Observer[O]): Unit = outObs.removeObserver(observer)

  override def now(): Option[O] = outObs.now()

  override def addNextChangeObserver(observer: Observer[O]): Unit = outObs.addNextChangeObserver(observer)

}