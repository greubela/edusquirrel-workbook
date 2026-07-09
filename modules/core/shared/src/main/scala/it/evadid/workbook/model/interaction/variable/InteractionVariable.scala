package it.evadid.workbook.model.interaction.variable

import it.evadid.core.datastructures.state.async.AsyncDataState.AsyncDataSuccess
import it.evadid.core.datastructures.state.async.{AsyncData, AsyncDataState, AsyncState}
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.state.observable.ObserverDerivationLogic.DeriveOnlyLastValues
import it.evadid.core.datastructures.state.{ExecutionMethod, State}
import it.evadid.core.util.*
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.{PrintToStdLogger, SyncLogger}
import it.evadid.workbook.model.abstractions.WorkbookInteractionElement
import it.evadid.workbook.model.interaction.*
import it.evadid.workbook.model.interaction.sync.SyncInformation.{SyncCache, SyncInformationWithContext}
import it.evadid.workbook.model.interaction.sync.*
import it.evadid.workbook.model.interaction.variable.InteractionVariableState.{DesignatedInteractionState, InteractionVariableStateChanged}

import java.time.LocalDateTime

case class InteractionVariable[T](underlyingInteraction: WorkbookInteractionElement[T], debug: Boolean = false) {

  private val defaultHistory = InteractionVariableHistory[T](Set(InteractionVariableState[T](underlyingInteraction.defaultValue, UpdateImportance.DEFAULT, LocalDateTime.now())))

  private val innerState: State[InteractionVariableHistory[T]] = State(defaultHistory)
  private val syncControl: State[Option[SyncControl]] = State(None)

  private def safeLastState: InteractionVariableState[T]  = innerState.now().lastStateOption.getOrElse(InteractionVariableState[T](underlyingInteraction.defaultValue, UpdateImportance.DEFAULT, LocalDateTime.now()))

  val fallbackLogger: SyncLogger = SyncLogger(Logger.withNameAndPrefixes(Some(s"InteractionVariableFallbackLogger(${keyForSerialization})"), PrintToStdLogger.printEverything))

  def useLogger: SyncLogger = syncControl.now().map(_.syncLogger).getOrElse(fallbackLogger)

  val keyForSerialization: String = underlyingInteraction.id + "_history"

  lazy val observableValue: ObservableValue[T] = innerState.observable.deriveValue(_.lastStateOption.getOrElse(safeLastState).value, ExecutionMethod.executeSync, DeriveOnlyLastValues)

  lazy val asAsync: AsyncData[Nothing, T] = {
    val obsState: ObservableValue[AsyncDataState[Nothing, T]] = observableValue.deriveValue(cur => AsyncDataSuccess(cur), ExecutionMethod.executeSync, DeriveOnlyLastValues)
    AsyncState(obsState)
  }

  lazy val tmpObservable: ObservableValue[InteractionVariableHistory[T]] = innerState.observable

  def createBoundStateWithUpdateLogic(relevanceFunc: InteractionVariableStateChanged[T] => UpdateImportance): State[T] = this.synchronized {
    val outerState = State[T](currentValue)
    innerState.observable.addObserver(newValue => if (newValue != outerState.now()) outerState.set(currentValue))
    outerState.observable.addObserver(newValue => if (newValue != currentValue) {
      val last = safeLastState
      val newState = DesignatedInteractionState(newValue, LocalDateTime.now())
      val importance = relevanceFunc(InteractionVariableStateChanged(last, newState))
      setStateFromUserInteraction(newValue, importance, newState.timestamp)
    })
    outerState
  }

  def createBoundStateWithUpdateImportance(importance: UpdateImportance): State[T] = this.synchronized {
    val outerState = State[T](currentValue)
    innerState.observable.addObserver(newValue => if (newValue != outerState.now()) outerState.set(currentValue))
    outerState.observable.addObserver(newValue => if (newValue != currentValue) setStateFromUserInteraction(newValue, importance, LocalDateTime.now()))
    outerState
  }

  def history: InteractionVariableHistory[T] = this.synchronized {
    innerState.now()
  }

  def serializedHistory: InteractionVariableHistorySerialized = this.synchronized {
    innerState.now().serialized(underlyingInteraction.serializer)
  }

  def currentValue: T = this.synchronized {
    safeLastState.value
  }

  def addHistory(history: InteractionVariableHistorySerialized): Unit = this.synchronized {
    innerState.update(_.withAddedEvents(useLogger, underlyingInteraction.serializer, history))
  }

  def executeLoad(syncCache: List[SyncCache]): Unit = this.synchronized {
    syncCache.foreach((curCache: SyncCache) => {
      val parsed: SyncInformation.SyncFetchedHistory[T] = curCache.typedHistory[T](keyForSerialization, underlyingInteraction.serializer)
      if (debug && parsed.unparsableElements.states.nonEmpty) {
        useLogger.logWarn("InteractionVariable '" + keyForSerialization + "' could not parse " + parsed.unparsableElements.states.size + " elements!")
      }
      innerState.update(_.withAddedEvents(parsed.typedElements))
    })
  }

  def resetHistoryAndSyncControl(newSyncControl: Option[SyncControl]): Unit = this.synchronized {
    val wasEmpty: Boolean = syncControl.now().isEmpty
    syncControl.set(newSyncControl)
    innerState.set(defaultHistory)

    if (wasEmpty) useLogger.logAllFrom(fallbackLogger)
  }

  def updateStateFromUserInteraction(updater: T => T, updateSize: UpdateImportance, timestamp: LocalDateTime = LocalDateTime.now()): Unit = this.synchronized {
    val nextState: T = updater(safeLastState.value)
    setStateFromUserInteraction(nextState, updateSize, timestamp)
  }

  def setStateFromUserInteraction(newValue: T, updateSize: UpdateImportance, timestamp: LocalDateTime = LocalDateTime.now()): Unit = this.synchronized {

    //useLogger.logInfo(s"InteractionVariable.updateStateFromUserInteraction(${innerState.now().lastState.value} -> $newValue ($timestamp, $updateSize)")
    try {
      val lastKnown: InteractionVariableState[T] = safeLastState
      if (newValue != lastKnown.value || updateSize != lastKnown.updateImportance) {
        val newInteractionState = InteractionVariableState[T](newValue, updateSize, timestamp)
        innerState.update(_.withAddedEvent(newInteractionState))
        if (syncControl.now().nonEmpty) {
          syncControl.now().foreach(_.requestStore(this, false))
        } else {
          useLogger.logWarn(s"User Interaction Updates at ${timestamp} was not committed because no syncTarget was available!")
        }
      } else {
        useLogger.logInfo(s"InteractionVariable.updateStateFromUserInteraction was ignored because it was identical to the last known value!")
      }
    } catch case e: Exception => {
      e.printStackTrace()
      useLogger.logExceptionWarn(s"InteractionVariable.updateStateFromUserInteraction failed with exception", e)
      throw e
    }
  }


}
