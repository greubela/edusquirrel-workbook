package it.evadid.workbook.model.interaction.variable

import it.evadid.workbook.model.interaction.*
import it.evadid.workbook.model.interaction.variable.*
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.state.{State, Subscription}
import it.evadid.core.util.io.{Serializer, TypeConverter}
import it.evadid.workbook.model.interaction.sync.UpdateImportance.*
import it.evadid.workbook.model.interaction.sync.{SyncInformation, SyncStrategy, UpdateImportance}

import java.time
import java.time.LocalDateTime
import scala.collection.mutable

case class InteractionVariable[T](underlyingInteraction: WorkbookInteraction[T], private val initStorageState: InteractionVariableStorage[T]) {

  private val innerState = State[InteractionVariableStorage[T]](initStorageState)

  private val syncSources = State[List[SyncInformation]](List())

  def createBoundStateWithUpdateImportance(importance: UpdateImportance): State[T] = synchronized {
    val outerState = State[T](currentValue)
    innerState.observable.addObserver(newValue => if (newValue != outerState.now()) outerState.set(currentValue))
    outerState.observable.addObserver(newValue => if (newValue != currentValue) setStateFromUserInteraction(newValue, importance, LocalDateTime.now()))
    //outerVar.signal.foreach(newValue => if (newValue != currentValue) setStateFromUserInteraction(newValue, importance, LocalDateTime.now()))
    outerState
  }

  def currentValue: T = synchronized {
    innerState.now().lastState.value
  }

  def addListener(onNewStateArrived: InteractionVariableState[T] => Any): Subscription[InteractionVariableState[T]] = {
    println("InteractionVariable::addListener does not work yet (:")
    //innerState.observable.addObserver(newState => println("newState: " + newState))
    new Subscription[InteractionVariableState[T]](null, null)
  }

  def syncFromAll(): Unit = synchronized {
    //innerState.update(_.withSyncFromAll())
    println("remember to re-implement InteractionVariable::SyncFromAll")
  }

  def syncToAll(): Unit = synchronized {
    //innerState.now().syncToAll()
    println("remember to re-implement InteractionVariable::SyncToAll")
  }

  /*
    def syncToAll(): Unit = {
    syncSources.foreach(curInfo => {
      val serialized = serializedWithStrategy(curInfo.syncStrategy)
      curInfo.syncSource.syncTo(keyForSerialization, serialized.toString)
    })
    println("[INFO] history '" + keyForSerialization + "' changed, synced to " + syncSources.size + " sources") //, current value: \n" + io.serialize(underlyingVar.now()) + ")")
  }

  def withSyncFromAll(): InteractionVariableStorage[T] = {
    val syncedFromHistory = mutable.ListBuffer[InteractionVariableState[T]]()
    syncSources.foreach(syncInfo => {
      val eventStr = syncInfo.syncSource.syncKeyFrom(keyForSerialization)
      if (eventStr.nonEmpty) {
        val serializedHistory = SerializedInteractionHistory(eventStr.get)
        serializedHistory.states.foreach(curState => syncedFromHistory += InteractionVariableState(io, curState))
      }
    })
    withAdditionalStates(syncedFromHistory.toList)
  }
   */

  def resetInteractionVariable(newDefaultValue: T, newSyncSources: List[SyncInformation]): Unit = synchronized {
    innerState.update(_.afterReset(newDefaultValue))
    ???
  }

  /*def createBoundVarWithUpdateImportance(importance: UpdateImportance): Var[T] = synchronized {
    val outerVar = Var[T](currentValue)
    innerState.observable.addObserver(newValue => if (newValue != outerVar.now()) outerVar.set(currentValue))
    outerVar.signal.foreach(newValue => if (newValue != currentValue) setStateFromUserInteraction(newValue, importance, LocalDateTime.now()))
    outerVar
  }*/

  def updateStateFromUserInteraction(updater: T => T, updateSize: UpdateImportance, timestamp: LocalDateTime = LocalDateTime.now()): Unit = synchronized {
    val nextState = updater(innerState.now().lastState.value)
    setStateFromUserInteraction(nextState, updateSize, timestamp)
  }

  def setStateFromUserInteraction(newValue: T, updateSize: UpdateImportance, timestamp: LocalDateTime = LocalDateTime.now()): Unit = synchronized {
    // println("InteractionVariable.updateStateFromUserInteraction: " + newValue + " (" + updateSize + ")")
    val lastKnown = innerState.now().lastState
    if (newValue != lastKnown.value || updateSize != lastKnown.updateImportance) {
      val newInteractionState = InteractionVariableState[T](newValue, updateSize, timestamp)
      innerState.update(_.withAdditionalStates(List(newInteractionState)))
      updateSize match {
        case UpdateImportance.MAJOR => syncToAll()
        case UpdateImportance.MINOR => syncToAll()
        case UpdateImportance.TEMPORARY => syncToAll()
        case UpdateImportance.DEFAULT => syncToAll()
      }
    }
  }

  def serializeHistory(): SerializedInteractionHistory = synchronized {
    innerState.now().serialized
  }

  def addHistory(serializedHistory: SerializedInteractionHistory): Unit = synchronized {
    innerState.update(_.withDeserializedHistory(serializedHistory))
  }

}

object InteractionVariable {

  def apply[T](interaction: WorkbookInteraction[T], io: Serializer[T]): InteractionVariable[T] = {
    //val sync = interaction.fullInfo.current.allSyncSources
    val storage = InteractionVariableStorage(interaction.id + "_history", interaction.defaultValue, io)
    InteractionVariable(interaction, storage)
  }


}