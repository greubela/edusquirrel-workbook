package it.evadid.workbook.model.interaction.variable

import it.evadid.core.datastructures.state.ObserverDerivationLogic.DeriveOnlyLastValues
import it.evadid.core.datastructures.state.{ExecutionMethod, ObservableValue, State, Subscription}
import it.evadid.core.util.*
import it.evadid.workbook.model.interaction.*
import it.evadid.workbook.model.interaction.sync.{SyncInformation, UpdateImportance}
import it.evadid.workbook.model.interaction.variable.*
import it.evadid.workbook.model.interaction.variable.InteractionVariableState.{DesignatedInteractionState, InteractionVariableStateChanged}

import java.time.LocalDateTime
import scala.collection.mutable

case class InteractionVariable[T](underlyingInteraction: WorkbookInteraction[T], debug: Boolean = false) {

  private val defaultHistory = InteractionVariableHistory[T](Set(InteractionVariableState[T](underlyingInteraction.defaultValue, UpdateImportance.DEFAULT, LocalDateTime.now())))

  private val innerState: State[InteractionVariableHistory[T]] = State(defaultHistory)
  private val syncSources = State[List[SyncInformation]](List())

  val keyForSerialization: String = underlyingInteraction.id + "_history"

  lazy val observableValue: ObservableValue[T] = innerState.observable.deriveValue(_.lastState.value, ExecutionMethod.executeSync, DeriveOnlyLastValues)

  lazy val tmpObservable: ObservableValue[InteractionVariableHistory[T]] = innerState.observable


  def createBoundStateWithUpdateLogic(relevanceFunc: InteractionVariableStateChanged[T] => UpdateImportance): State[T] = this.synchronized {
    val outerState = State[T](currentValue)
    innerState.observable.addObserver(newValue => if (newValue != outerState.now()) outerState.set(currentValue))
    outerState.observable.addObserver(newValue => if (newValue != currentValue) {
      val last = innerState.now().lastState
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
    //outerVar.signal.foreach(newValue => if (newValue != currentValue) setStateFromUserInteraction(newValue, importance, LocalDateTime.now()))
    outerState
  }

  def history: InteractionVariableHistory[T] = this.synchronized {
    innerState.now()
  }

  def serializedHistory: InteractionVariableHistorySerialized = this.synchronized {
    innerState.now().serialized(underlyingInteraction.serializer)
  }

  def currentValue: T = this.synchronized {
    innerState.now().lastState.value
  }

  def addHistory(history: InteractionVariableHistorySerialized): Unit = this.synchronized {
    innerState.update(_.withAddedEvents(underlyingInteraction.serializer, history))
  }

  def syncToAll(forceFlush: Boolean = false): Unit = this.synchronized {
    syncSources.now().foreach(curInfo => {
      val serialized = innerState.now().serializedWithStrategy(curInfo.syncStrategy, underlyingInteraction.serializer)
      curInfo.syncSource.syncTo(keyForSerialization, serialized.toString)
    })
    if (debug) {
      println("[INFO] history '" + keyForSerialization + "' changed, synced to " + syncSources.now().size + " sources"
        + ", current value: " + innerState.now().lastState.value
        + ", last update time: " + InfoUtil.datetimeFormattedForLog(innerState.now().lastState.timestamp) + ", total events: " + innerState.now().events.size + ")")
    }
  }

  def syncFromAll(): Unit = this.synchronized {
    val syncedFromHistory = mutable.ListBuffer[InteractionVariableState[T]]()
    syncSources.now().foreach(syncInfo => {
      syncInfo.syncSource.syncKeyFrom(keyForSerialization).foreach(eventStr => {
        try {
          val serializedHistory = InteractionVariableHistorySerialized(eventStr)
          syncedFromHistory.addAll(serializedHistory.deserialize(underlyingInteraction.serializer).events)
        } catch {
          case err: Throwable => println("[ERROR] could not deserialize " + eventStr + " with " + underlyingInteraction.serializer)
        }
      })
    })
    innerState.update(_.withAddedEvents(syncedFromHistory.toSet))
  }

  def resetHistory(): Unit = this.synchronized {
    innerState.set(defaultHistory)
  }

  def resetInteractionVariable(newSyncSources: List[SyncInformation]): Unit = this.synchronized {
    syncSources.set(List())
    innerState.set(defaultHistory)
    syncSources.set(newSyncSources)
  }

  def updateStateFromUserInteraction(updater: T => T, updateSize: UpdateImportance, timestamp: LocalDateTime = LocalDateTime.now()): Unit = this.synchronized {
    val nextState = updater(innerState.now().lastState.value)
    setStateFromUserInteraction(nextState, updateSize, timestamp)
  }

  def setStateFromUserInteraction(newValue: T, updateSize: UpdateImportance, timestamp: LocalDateTime = LocalDateTime.now()): Unit = this.synchronized {
    println("InteractionVariable.updateStateFromUserInteraction: " + newValue + " (" + updateSize + ", oldValue: " + innerState.now().lastState.value + ")")

    val lastKnown = innerState.now().lastState
    if (newValue != lastKnown.value || updateSize != lastKnown.updateImportance) {
      val newInteractionState = InteractionVariableState[T](newValue, updateSize, timestamp)
      innerState.update(_.withAddedEvent(newInteractionState))
      syncToAll()
    } else {
      // println("Update supressed!")
    }
  }


}