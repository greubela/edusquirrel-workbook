package it.evadid.workbook.interaction.variable

import it.evadid.core.datastructures.state.async.AsyncDataState.AsyncDataSuccess
import it.evadid.core.datastructures.state.async.{AsyncData, AsyncDataState, AsyncState}
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.state.observable.ObserverDerivationLogic.DeriveOnlyLastValues
import it.evadid.core.datastructures.state.{ExecutionMethod, State}
import it.evadid.core.util.io.Serializer
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.WorkbookInteractionElementFull
import it.evadid.workbook.interaction.sync.{SyncControl, UpdateImportance}
import it.evadid.workbook.interaction.variable.InteractionVariableState.{DesignatedInteractionState, InteractionVariableStateChanged}
import it.evadid.workbook.meta.ExecutionContextInformation
import it.evadid.workbook.meta.ExecutionContextInformation.default.syncControl
import upickle.default.*

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext

object InteractionVariable {

}

type InteractionVariableUntyped = InteractionVariable[?, ?, ?]

case class InteractionVariable[T: ReadWriter, GA: ReadWriter, SA: ReadWriter](underlyingInteraction: WorkbookInteractionElementFull[T, GA, SA], debug: Boolean = false) {

  given ec: ExecutionContext = ExecutionContext.global

  private val historySerializer: Serializer[InteractionVariableHistory[T, GA, SA]] = Serializer.fromUpickleJson(InteractionVariableHistory.given_ReadWriter_InteractionVariableHistory[T, GA, SA])

  def currentHistory: InteractionVariableHistory[T, GA, SA] = this.synchronized {
    innerState.now()
  }

  def currentValue: T = this.synchronized {
    safeLastState.value
  }


  private val defaultState = InteractionVariableState[T](underlyingInteraction.defaultValue, UpdateImportance.DEFAULT, LocalDateTime.now())
  private val defaultHistory: InteractionVariableHistory[T, GA, SA] = InteractionVariableHistory[T, GA, SA](List(defaultState).toSet)

  private def safeLastState: InteractionVariableState[T] = innerState.now().historyStates.lastStateOption.getOrElse(defaultState)

  private val innerState: State[InteractionVariableHistory[T, GA, SA]] = State(defaultHistory)

  val control: InteractionScaffoldingAndGradingControl[T, GA, SA] = InteractionScaffoldingAndGradingControl(this)

  val keyForSerialization: String = underlyingInteraction.elementId + "_history"
  val serializerHistory: Serializer[InteractionVariableHistory[T, GA, SA]] = Serializer.fromUpickleJson(InteractionVariableHistory.given_ReadWriter_InteractionVariableHistory[T, GA, SA])

  lazy val observableState: ObservableValue[InteractionVariableHistory[T, GA, SA]] = innerState.observable

  lazy val observableValue: ObservableValue[T] = this.synchronized {
    innerState.observable.deriveValue(_.historyStates.lastStateOption.getOrElse(safeLastState).value, ExecutionMethod.executeSync, DeriveOnlyLastValues)
  }

  lazy val asAsync: AsyncData[Nothing, T] = this.synchronized {
    val obsState: ObservableValue[AsyncDataState[Nothing, T]] = observableValue.deriveValue(cur => AsyncDataSuccess(cur), ExecutionMethod.executeSync, DeriveOnlyLastValues)
    AsyncState(obsState)
  }

  def createBoundStateWithUpdateLogic(relevanceFunc: InteractionVariableStateChanged[T] => UpdateImportance, context: ExecutionContextInformation): State[T] = this.synchronized {
    val outerState = State[T](currentValue)
    innerState.observable.addObserver(newValue => if (newValue != outerState.now()) outerState.set(currentValue))
    outerState.observable.addObserver(newValue => if (newValue != currentValue) {
      val last = safeLastState
      val newState = DesignatedInteractionState(newValue, LocalDateTime.now())
      val importance = relevanceFunc(InteractionVariableStateChanged(last, newState))
      setStateFromUserInteraction( newValue, importance, newState.timestamp, context)
    })
    outerState
  }

  def createBoundStateWithUpdateImportance(importance: UpdateImportance, context: ExecutionContextInformation): State[T] = this.synchronized {
    createBoundStateWithUpdateLogic(_ => importance, context)
  }

  def addSerializedHistory(logger: Logger, serializedHistory: String, executionContextInformation: ExecutionContextInformation): Unit = this.synchronized {
    val deserialized = try {
      historySerializer.deserialize(serializedHistory)
    } catch case (err: Throwable) => {
      logger.logExceptionWarn("Ignoring serialized history as it was not parsable!", err)
      InteractionVariableHistory.empty[T, GA, SA]
    }
    updateHistory(_.mergeWith(deserialized), executionContextInformation)
  }

  def updateHistory(func: InteractionVariableHistory[T, GA, SA] => InteractionVariableHistory[T, GA, SA], executionContextInformation: ExecutionContextInformation): Unit = this.synchronized {
    innerState.update(func)
    if (executionContextInformation.syncControl.nonEmpty) {
      executionContextInformation.syncControl.get.requestStore(this)
    }
  }

  /*def withAddedEvents(logger: Logger, serializedHistory: String): Unit = {
    try {
      val res = deserializeHistory(serializedHistory)
      innerState.update(current => current.withAddedEvents(res.events).copy(
        gradings = current.gradings ++ res.gradings, scaffoldings = current.scaffoldings ++ res.scaffoldings
      ))
    } catch case (err: Throwable) => {
      logger.logExceptionWarn(s"InteractionVariableHistory -> withAddedEvents: cannot parse serializedHistory, ignoring content'${serializedHistory}'", err)
      this
    }
  }*/

  def executeLoad(syncControl: SyncControl): Unit = this.synchronized {
    val histories = syncControl.createCurrentReport(this).curRemoteHistory.values
      .flatMap(_.lastKnownRemoteValue.map(_.dataValue)).toVector
    innerState.update(_.mergeWith(histories))
  }

  def resetLocalHistory(): Unit = this.synchronized {
    innerState.set(defaultHistory)
  }

  def updateStateFromUserInteraction(updater: T => T, updateSize: UpdateImportance, timestamp: LocalDateTime = LocalDateTime.now(), context: ExecutionContextInformation): Unit = this.synchronized {
    val nextState: T = updater(safeLastState.value)
    setStateFromUserInteraction(nextState, updateSize, timestamp, context)
  }


  def setStateFromUserInteraction(newValue: T, updateSize: UpdateImportance, timestamp: LocalDateTime = LocalDateTime.now(), context: ExecutionContextInformation): Unit = this.synchronized {
    //useLogger.logInfo(s"InteractionVariable.updateStateFromUserInteraction(${innerStateHistory.now().lastState.value} -> $newValue ($timestamp, $updateSize)")
    try {
      val lastKnown: InteractionVariableState[T] = safeLastState
      if (newValue != lastKnown.value || updateSize != lastKnown.updateImportance) {
        val newInteractionState = InteractionVariableState[T](newValue, updateSize, timestamp)
        updateHistory(_.updateStates(_ + newInteractionState), context)
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
