package workbook.model.interaction

import util.serializing.*
import com.raquo.airstream.ownership.Owner
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import datastructures.core.chat.MessengerModel
import datastructures.core.chat.MessengerModel.Message
import util.serializing.{Serializer, TypeConverter}
import workbook.model.abstractions.WorkbookInteraction
import workbook.model.interaction.InteractionVariable.*
import workbook.model.interaction.InteractionVariableState.SerializedExerciseVariableState
import workbook.model.interaction.history.*
import workbook.model.interaction.history.UpdateImportance.DEFAULT
import workbook.model.interaction.sync.*

import scala.collection.mutable

case class InteractionVariable[T](underlyingInteraction: WorkbookInteraction[T], private val initStorageState: InteractionVariableStorage[T]) {

  private implicit val appOwner: Owner = unsafeWindowOwner

  private val innerState: Var[InteractionVariableStorage[T]] = {
    val withLoaded = initStorageState.withSyncFromAll().withCleanedDefaultStates()
    Var(withLoaded)
  }
  lazy val interactionSignal: StrictSignal[T] = innerState.signal.mapLazy(_.lastState.value)

  def currentValue: T = synchronized {
    interactionSignal.now()
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
    val outerVar = Var[T](interactionSignal.now())
    outerVar.signal.foreach(newValue => updateStateFromUserInteraction(newValue, System.currentTimeMillis(), importance))
    interactionSignal.foreach(newValue => if (newValue != outerVar.now()) outerVar.set(newValue))
    outerVar
  }

  def updateStateFromUserInteraction(newValue: T, epochTimestampMillis: Long, updateSize: UpdateImportance): Unit = synchronized {
    // println("InteractionVariable.updateStateFromUserInteraction: " + newValue + " (" + updateSize + ")")
    val lastKnown = innerState.now().lastState
    if (newValue != lastKnown.value || updateSize != lastKnown.updateImportance) {
      val newInteractionState = InteractionVariableState[T](newValue, epochTimestampMillis, updateSize)
      innerState.update(_.withAdditionalStates(List(newInteractionState)))
      updateSize match {
        case UpdateImportance.MAJOR => syncToAll()
        case UpdateImportance.MINOR => syncToAll()
        case UpdateImportance.TEMPORARY =>
        case UpdateImportance.DEFAULT =>
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
      history.maxBy(_.epochTimestampMillis)
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
      println("[INFO] history '" + keyForSerialization + "' changed, synced to " + syncSources.size + " sources")//, current value: \n" + io.serialize(underlyingVar.now()) + ")")
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
      val history: List[InteractionVariableState[T]] = List(InteractionVariableState[T](defaultValue, System.currentTimeMillis(), UpdateImportance.DEFAULT))
      InteractionVariableStorage[T](syncId, history, syncSources, io)
    }
  }

  def apply[T](interaction: WorkbookInteraction[T], io: Serializer[T]): InteractionVariable[T] = {
    val sync = interaction.fullInfo.current.allSyncSources
    val storage = InteractionVariableStorage(interaction.id + "_history", interaction.defaultValue, sync, io)
    InteractionVariable(interaction, storage)
  }





  /*
    private def serializeHistory[T](history: List[InteractionVariableState[T]], serializer: Serializer[T]): String = {
      val serializedHistory: List[SerializedExerciseVariableState] = history.map(curState =>
        SerializedExerciseVariableState(curState.epochTimestampMillis, serializer.serialize(curState.value), curState.updateImportance)
      )
    }

    private def deserializeHistory[T](serializedHistory: String, serializer: Serializer[T]): Set[InteractionVariableState[T]] = {
      val deserialized = read[List[SerializedExerciseVariableState]](serializedHistory)
      deserialized.map(curState =>
        InteractionVariableState(
          serializer.deserialize(curState.serializedValue),
          curState.epochTimestampMillis,
          curState.updateImportance
        )
      ).toSet
    }*/


}