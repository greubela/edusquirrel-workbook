package it.evadid.workbook.interaction.variable

import it.evadid.core.datastructures.state.async.AsyncDataState.AsyncDataSuccess
import it.evadid.core.datastructures.state.async.{AsyncData, AsyncDataState, AsyncState}
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.state.observable.ObserverDerivationLogic.DeriveOnlyLastValues
import it.evadid.core.datastructures.state.{ExecutionMethod, State}
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.interaction.sync.UpdateImportance
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import it.evadid.workbook.interaction.variable.InteractionVariableState.{DesignatedInteractionState, InteractionVariableStateChanged}
import it.evadid.workbook.meta.ExecutionContextInformation

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext

object InteractionVariable {


}

case class InteractionVariable[F <: InteractionFormat](underlyingInteraction: WorkbookInteractionElement[F], debug: Boolean = false) {

  given ec: ExecutionContext = ExecutionContext.global

  def currentHistory: InteractionVariableHistory[F] = this.synchronized {
    innerState.now()
  }

  def currentValue: F#InteractionStateType = this.synchronized {
    safeLastState.value
  }


  private val defaultState = InteractionVariableState[F#InteractionStateType](underlyingInteraction.defaultValue, UpdateImportance.DEFAULT, LocalDateTime.now())
  private val defaultHistory: InteractionVariableHistory[F] = InteractionVariableHistory.fromResults[F](List(defaultState).toSet)

  def safeLastState: InteractionVariableState[F#InteractionStateType] = innerState.now().historyStates.lastStateOption.getOrElse(defaultState)

  private val innerState: State[InteractionVariableHistory[F]] = State(defaultHistory)

  val control: InteractionScaffoldingAndGradingControl[F] = InteractionScaffoldingAndGradingControl(this)

  val keyForSerialization: String = underlyingInteraction.elementId + "_history"


  lazy val observableHistoryWithDefault: ObservableValue[InteractionVariableHistory[F]] = innerState.observable.deriveValue(curHistory => {
    if (curHistory.historyStates.lastStateOption.nonEmpty) curHistory
    else defaultHistory.addState(defaultState)
  })

  lazy val observableValue: ObservableValue[F#InteractionStateType] = this.synchronized {
    observableHistoryWithDefault.deriveValue(_.historyStates.lastStateOption.getOrElse(safeLastState).value, ExecutionMethod.executeSync, DeriveOnlyLastValues)
  }

  lazy val asAsync: AsyncData[Nothing, F#InteractionStateType] = this.synchronized {
    val obsState: ObservableValue[AsyncDataState[Nothing, F#InteractionStateType]] = observableValue.deriveValue(cur => AsyncDataSuccess(cur), ExecutionMethod.executeSync, DeriveOnlyLastValues)
    AsyncState(obsState)
  }

  def createBoundStateWithUpdateLogic(relevanceFunc: InteractionVariableStateChanged[F#InteractionStateType] => UpdateImportance, context: ExecutionContextInformation): State[F#InteractionStateType] = this.synchronized {
    val outerState = State[F#InteractionStateType](currentValue)
    observableValue.addObserver(newValue => if (newValue != outerState.now()) outerState.set(currentValue))
    outerState.observable.addObserver(newValue => if (newValue != currentValue) {
      val last = safeLastState
      val newState = DesignatedInteractionState(newValue, LocalDateTime.now())
      val importance = relevanceFunc(InteractionVariableStateChanged(last, newState))
      setStateFromUserInteraction(newValue, importance, newState.timestamp, context)
    })
    outerState
  }

  def createBoundStateWithUpdateImportance(importance: UpdateImportance, context: ExecutionContextInformation): State[T] = this.synchronized {
    createBoundStateWithUpdateLogic(_ => importance, context)
  }

  def addSerializedHistory(logger: Logger, serializedHistory: String, executionContextInformation: ExecutionContextInformation): Unit = this.synchronized {
    val deserialized = try {
      InteractionHistoryJson(underlyingInteraction.format, serializedHistory).toTyped
    } catch case (err: Throwable) => {
      logger.logExceptionWarn("Ignoring serialized history as it was not parsable!", err)
      InteractionVariableHistory.empty[F]
    }
    updateHistory(_.mergeWith(deserialized), executionContextInformation)
  }

  /** Add history from an InteractionHistoryJson, using the format's serializer. */
  def addSerializedHistoryFromInteractionHistoryJson(logger: Logger, historyJson: InteractionHistoryJson[F], executionContextInformation: ExecutionContextInformation): Unit = this.synchronized {
    val deserialized = try {
      historyJson.toTyped
    } catch case (err: Throwable) => {
      logger.logExceptionWarn("Ignoring serialized history from InteractionHistoryJson as it was not parsable!", err)
      InteractionVariableHistory.empty[F]
    }
    updateHistory(_.mergeWith(deserialized), executionContextInformation)
  }

  def updateHistory(func: InteractionVariableHistory[F] => InteractionVariableHistory[F], executionContextInformation: ExecutionContextInformation): Unit = this.synchronized {
    innerState.update(oldVal => {
      val transformedHistory = func(oldVal)
      val withCurrentState = if (transformedHistory.historyStates.lastStateOption.isEmpty)
        transformedHistory.addState(defaultState)
      else transformedHistory
      withCurrentState.normalize()
    })
    if (executionContextInformation.syncControl.nonEmpty) {
      executionContextInformation.syncControl.get.store(this, false)
    }
    control.getOrCalculateGrading(executionContextInformation, false)
  }

  def resetLocalHistory(): Unit = this.synchronized {
    updateHistory(_ => defaultHistory, ExecutionContextInformation.default)
  }

  def updateStateFromUserInteraction(updater: F#InteractionStateType => F#InteractionStateType, updateSize: UpdateImportance, timestamp: LocalDateTime = LocalDateTime.now(), context: ExecutionContextInformation): Unit = this.synchronized {
    val nextState: F#InteractionStateType = updater(safeLastState.value)
    setStateFromUserInteraction(nextState, updateSize, timestamp, context)
  }


  def setStateFromUserInteraction(newValue: F#InteractionStateType, updateSize: UpdateImportance, timestamp: LocalDateTime = LocalDateTime.now(), context: ExecutionContextInformation): Unit = this.synchronized {
    //useLogger.logInfo(s"InteractionVariable.updateStateFromUserInteraction(${innerStateHistory.now().lastState.value} -> $newValue ($timestamp, $updateSize)")
    try {
      val lastKnown: InteractionVariableState[F#InteractionStateType] = safeLastState
      if (newValue != lastKnown.value || updateSize != lastKnown.updateImportance) {
        val newInteractionState = InteractionVariableState[F#InteractionStateType](newValue, updateSize, timestamp)
        updateHistory(_.addState(newInteractionState), context)
      } else if (context.syncControl.nonEmpty) {
        context.syncControl.get.syncLogger.logInfo(s"InteractionVariable.updateStateFromUserInteraction was ignored because it was identical to the last known value!")
      }
    } catch case e: Exception => {
      if (context.syncControl.nonEmpty) {
        context.syncControl.get.syncLogger.logExceptionWarn(s"InteractionVariable.updateStateFromUserInteraction failed with exception", e)
      }
      throw e
    }
  }

}
