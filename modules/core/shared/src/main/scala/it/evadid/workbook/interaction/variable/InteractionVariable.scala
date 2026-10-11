package it.evadid.workbook.interaction.variable

import it.evadid.core.datastructures.state.async.AsyncDataState.AsyncDataSuccess
import it.evadid.core.datastructures.state.async.{AsyncData, AsyncDataState, AsyncState}
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.state.observable.ObserverDerivationLogic.DeriveOnlyLastValues
import it.evadid.core.datastructures.state.{ExecutionMethod, State}
import it.evadid.workbook.abstractions.{WorkbookInteractionElement, WorkbookInteractionElementFull}
import it.evadid.workbook.abstractions.grading.{GradedState, GradingResult}
import it.evadid.workbook.interaction.sync.{SyncControl, UpdateImportance}
import it.evadid.workbook.interaction.variable.InteractionVariableState.{DesignatedInteractionState, InteractionVariableStateChanged}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object InteractionVariable {

}

type InteractionVariableUntyped = InteractionVariable[?, ?, ?]

case class InteractionVariable[T, GA, SA](underlyingInteraction: WorkbookInteractionElementFull[T, GA, SA], debug: Boolean = false) {

  given ec: ExecutionContext = ExecutionContext.global

  private val defaultHistory = {
    val defaultState = InteractionVariableState[T](underlyingInteraction.defaultValue, UpdateImportance.DEFAULT, LocalDateTime.now())
    InteractionVariableHistory[T, GA, SA](Set(defaultState), Map(), Map())
  }

  private val innerState: State[InteractionVariableHistory[T, GA, SA]] = State(defaultHistory)

  private def safeLastState: InteractionVariableState[T] = innerState.now().lastStateOption.getOrElse(InteractionVariableState[T](underlyingInteraction.defaultValue, UpdateImportance.DEFAULT, LocalDateTime.now()))

  //  val fallbackLogger: SyncLogger = SyncLogger(Logger.withNameAndPrefixes(Some(s"InteractionVariableFallbackLogger(${keyForSerialization})"), PrintToStdLogger.printEverything))

  val keyForSerialization: String = underlyingInteraction.elementId + "_history"

  lazy val observableState: ObservableValue[InteractionVariableHistory[T, GA, SA]] = innerState.observable

  lazy val observableValue: ObservableValue[T] = innerState.observable.deriveValue(_.lastStateOption.getOrElse(safeLastState).value, ExecutionMethod.executeSync, DeriveOnlyLastValues)

  lazy val asAsync: AsyncData[Nothing, T] = {
    val obsState: ObservableValue[AsyncDataState[Nothing, T]] = observableValue.deriveValue(cur => AsyncDataSuccess(cur), ExecutionMethod.executeSync, DeriveOnlyLastValues)
    AsyncState(obsState)
  }


  def createBoundStateWithUpdateLogic(syncControl: SyncControl, relevanceFunc: InteractionVariableStateChanged[T] => UpdateImportance): State[T] = this.synchronized {
    val outerState = State[T](currentValue)
    innerState.observable.addObserver(newValue => if (newValue != outerState.now()) outerState.set(currentValue))
    outerState.observable.addObserver(newValue => if (newValue != currentValue) {
      val last = safeLastState
      val newState = DesignatedInteractionState(newValue, LocalDateTime.now())
      val importance = relevanceFunc(InteractionVariableStateChanged(last, newState))
      setStateFromUserInteraction(syncControl, newValue, importance, newState.timestamp)
    })
    outerState
  }

  def createBoundStateWithUpdateImportance(syncControl: SyncControl, importance: UpdateImportance): State[T] = this.synchronized {
    val outerState = State[T](currentValue)
    innerState.observable.addObserver(newValue => if (newValue != outerState.now()) outerState.set(currentValue))
    outerState.observable.addObserver(newValue => if (newValue != currentValue) setStateFromUserInteraction(syncControl, newValue, importance, LocalDateTime.now()))
    outerState
  }

  def history: InteractionVariableHistory[T, GA, SA] = this.synchronized {
    innerState.now()
  }

  /*def serializedHistory: InteractionVariableHistorySerialized = this.synchronized {
    innerState.now().serialized(underlyingInteraction.serializerInteractionContent)
  }*/

  def currentValue: T = this.synchronized {
    safeLastState.value
  }

  def updateHistory(func: InteractionVariableHistory[T, GA, SA] => InteractionVariableHistory[T, GA, SA]): Unit = this.synchronized {
    innerState.update(func)
  }

  def executeLoad(syncControl: SyncControl): Unit = this.synchronized {
    val toAdd: Set[InteractionVariableState[T]] = syncControl.createCurrentReport(this).allStatesEverywhere
    innerState.update(_.withAddedEvents(toAdd))
  }

  def resetLocalHistory(): Unit = this.synchronized {
    innerState.set(defaultHistory)
  }

  def updateStateFromUserInteraction(syncControl: SyncControl, updater: T => T, updateSize: UpdateImportance, timestamp: LocalDateTime = LocalDateTime.now()): Unit = this.synchronized {
    val nextState: T = updater(safeLastState.value)
    setStateFromUserInteraction(syncControl, nextState, updateSize, timestamp)

  }

  def startGrading(syncControl: SyncControl, forceGrading: Boolean = false): Unit = this.synchronized {
    val curState = currentValue
    val futures: Future[List[GradingResult[T, GA]]] = Future.traverse(underlyingInteraction.gradingElements)(_.gradeStateSafe(curState, forceGrading))
    futures.map(resList => GradedState[T, GA](curState, resList.toSet)).onComplete {
      case Success(res) => this.synchronized {
        innerState.update(_.updateGradings(_ ++ Map(curState -> res)))
      }
      case Failure(err) => syncControl.controlLogger.logExceptionWarn("Ignore Grading after error at Grading", err)
    }

  }

  def setStateFromUserInteraction(syncControl: SyncControl, newValue: T, updateSize: UpdateImportance, timestamp: LocalDateTime = LocalDateTime.now()): Unit = this.synchronized {
    //useLogger.logInfo(s"InteractionVariable.updateStateFromUserInteraction(${innerStateHistory.now().lastState.value} -> $newValue ($timestamp, $updateSize)")
    try {
      val lastKnown: InteractionVariableState[T] = safeLastState
      if (newValue != lastKnown.value || updateSize != lastKnown.updateImportance) {
        val newInteractionState = InteractionVariableState[T](newValue, updateSize, timestamp)
        innerState.update(_.withAddedEvent(newInteractionState))
        syncControl.requestStore(this)
      } else {
        syncControl.syncLogger.logInfo(s"InteractionVariable.updateStateFromUserInteraction was ignored because it was identical to the last known value!")
      }
    } catch case e: Exception => {
      e.printStackTrace()
      syncControl.syncLogger.logExceptionWarn(s"InteractionVariable.updateStateFromUserInteraction failed with exception", e)
      throw e
    }
  }


}
