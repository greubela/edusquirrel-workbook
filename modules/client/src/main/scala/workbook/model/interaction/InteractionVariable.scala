package workbook.model.interaction

import com.raquo.airstream.ownership.Owner
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.state.State
import it.evadid.core.util.io.{Serializer, TypeConverter}
import util.serializing.*
import workbook.model.abstractions.WorkbookInteraction
import workbook.model.interaction.InteractionVariable.*
import workbook.model.interaction.InteractionVariableState.SerializedExerciseVariableState
import workbook.model.interaction.history.*
import workbook.model.interaction.history.UpdateImportance.DEFAULT
import workbook.model.interaction.sync.*

import java.time
import java.time.LocalDateTime
import scala.collection.mutable

case class InteractionVariable[T](underlyingInteraction: WorkbookInteraction[T], private val initStorageState: InteractionVariableStorage[T]) {

  private implicit val appOwner: Owner = unsafeWindowOwner

  /*private val innerState: Var[InteractionVariableStorage[T]] = {
    val withLoaded = initStorageState.withSyncFromAll().withCleanedDefaultStates()
    Var(withLoaded)
  }*/

  private val innerState = State[InteractionVariableStorage[T]](initStorageState)

  lazy val interactionSignal: StrictSignal[T] = {
    createBoundVarWithUpdateImportance(UpdateImportance.TEMPORARY).signal
  }

  def currentValue: T = synchronized {
    innerState.now().lastState.value
  }

  def syncFromAll(): Unit = synchronized {
    innerState.update(_.withSyncFromAll())
  }

  def syncToAll(): Unit = synchronized {
    innerState.now().syncToAll()
  }

  def resetInteractionVariable(newDefaultValue: T, newSyncSources: List[SyncInformation]): Unit = synchronized {
    innerState.update(_.afterReset(newDefaultValue, newSyncSources))
  }

  def createBoundVarWithUpdateImportance(importance: UpdateImportance): Var[T] = synchronized {
    val outerVar = Var[T](currentValue)
    innerState.observable.addObserver(newValue => if (newValue != outerVar.now()) outerVar.set(currentValue))
    outerVar.signal.foreach(newValue => if (newValue != currentValue) setStateFromUserInteraction(newValue, importance, LocalDateTime.now()))
    outerVar
  }

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


  private[interaction] case class InteractionVariableStorage[T](keyForSerialization: String, history: List[InteractionVariableState[T]], syncSources: List[SyncInformation], io: Serializer[T]) {

    lazy val serialized: SerializedInteractionHistory = SerializedInteractionHistory(keyForSerialization, history.map(_.serialized(io)))

    def serializedWithStrategy(syncStrategy: SyncStrategy): SerializedInteractionHistory = {
      val syncedHistory = syncStrategy.selectEventsToSync(history)
      SerializedInteractionHistory(keyForSerialization, syncedHistory.map(_.serialized(io)))
    }

    def withDeserializedHistory(deserializedHistory: SerializedInteractionHistory): InteractionVariableStorage[T] =
      if (deserializedHistory.keyForSerialization != keyForSerialization) this
      else {
        val additionalStates = deserializedHistory.states.map(curStrState => InteractionVariableState(io, curStrState))
        withAdditionalStates(additionalStates)
      }

    def lastState: InteractionVariableState[T] = {
      /*if(keyForSerialization == "auto-id-1_history"){
        println("history: " + history.map(_.serialized(io)).mkString(","))
        println("lastState: " + history.maxBy(_.epochTimestampMillis).serialized(io))
      }*/
      history.maxBy(_.timestamp)
    }

    def afterReset(defaultValue: T, syncSources: List[SyncInformation]): InteractionVariableStorage[T] = {
      InteractionVariableStorage(keyForSerialization, defaultValue, syncSources, io)
    }

    def withAdditionalStates(additionalStates: List[InteractionVariableState[T]]): InteractionVariableStorage[T] = {
      val res = this.copy(history = history ++ additionalStates).withCleanedDefaultStates()
      val last1 = this.lastState
      val last2 = res.lastState
      //if (last1 != last2 || keyForSerialization == "auto-id-1_history") println("changed history: " + last1.serialized(io) + " -> " + last2.serialized(io))
      res
    }

    def withCleanedDefaultStates(): InteractionVariableStorage[T] = {
      if (!history.exists(_.updateImportance == DEFAULT) || !history.exists(_.updateImportance != DEFAULT)) {
        this
      } else {
        this.copy(history = history.filter(_.updateImportance != DEFAULT))
      }
    }

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
  }

  object InteractionVariableStorage {

    def apply[T](syncId: String, defaultValue: T, syncSources: List[SyncInformation], io: Serializer[T]): InteractionVariableStorage[T] = {
      val history: List[InteractionVariableState[T]] = List(InteractionVariableState[T](defaultValue, UpdateImportance.DEFAULT, LocalDateTime.now()))
      InteractionVariableStorage[T](syncId, history, syncSources, io)
    }
  }

  def apply[T](interaction: WorkbookInteraction[T], io: Serializer[T]): InteractionVariable[T] = {
    val sync = interaction.fullInfo.current.allSyncSources
    val storage = InteractionVariableStorage(interaction.id + "_history", interaction.defaultValue, sync, io)
    InteractionVariable(interaction, storage)
  }


}