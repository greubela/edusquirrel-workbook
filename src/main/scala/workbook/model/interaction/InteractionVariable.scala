package workbook.model.interaction


import com.raquo.airstream.ownership.Owner
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import upickle.default.*
import util.serializing.Serializer
import workbook.model.abstractions.WorkbookInteraction
import workbook.model.interaction.InteractionVariable.InteractionVariableStorage
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
    println("InteractionVariable.updateStateFromUserInteraction: " + newValue + " (" + updateSize + ")")
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


}

object InteractionVariable {

  private[interaction] case class InteractionVariableStorage[T](keyForSerialization: String, history: List[InteractionVariableState[T]], syncSources: List[SyncInformation], io: Serializer[T]) {

    def lastState: InteractionVariableState[T] = history.maxBy(_.epochTimestampMillis)

    def afterReset(defaultValue: T, syncSources: List[SyncInformation]): InteractionVariableStorage[T] = {
      InteractionVariableStorage(keyForSerialization, defaultValue, syncSources, io)
    }

    def withAdditionalStates(additionalStates: List[InteractionVariableState[T]]): InteractionVariableStorage[T] = {
      this.copy(history = history ++ additionalStates).withCleanedDefaultStates()
    }

    def withCleanedDefaultStates(): InteractionVariableStorage[T] = {
      if (!history.exists(_.updateImportance == DEFAULT) || !history.exists(_.updateImportance != DEFAULT)) {
        this
      } else {
        this.copy(history = history.filter(_.updateImportance != DEFAULT))
      }
    }

    def syncToAll(): Unit = {
      syncSources.foreach(syncInfo => {
        val eventsToSync = syncInfo.syncStrategy.selectEventsToSync(history)
        syncInfo.syncSource.syncTo(keyForSerialization, InteractionVariable.serializeHistory(eventsToSync, io))
      })
      //println("[INFO] history '" + keyForSerialization + "' changed, synced to " + syncSources.size + " sources")//, current value: \n" + io.serialize(underlyingVar.now()) + ")")
    }


    def withSyncFromAll(): InteractionVariableStorage[T] = {
      val syncedFromHistory = mutable.ListBuffer[InteractionVariableState[T]]()
      syncSources.foreach(syncInfo => {
        val eventStr = syncInfo.syncSource.syncKeyFrom(keyForSerialization)
        if (eventStr.nonEmpty) {
          val addToHistory = InteractionVariable.deserializeHistory(eventStr.get, io)
          syncedFromHistory ++= addToHistory
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


  private case class SerializedExerciseVariableState(
                                                      epochTimestampMillis: Long,
                                                      serializedValue: String,
                                                      updateImportance: UpdateImportance
                                                    )

  private given ReadWriter[UpdateImportance] = readwriter[String].bimap[UpdateImportance](_.toString, UpdateImportance.valueOf)

  private given ReadWriter[SerializedExerciseVariableState] = macroRW

  private def serializeHistory[T](history: List[InteractionVariableState[T]], serializer: Serializer[T]): String = {
    val serializedHistory = history.map(curState =>
      SerializedExerciseVariableState(curState.epochTimestampMillis, serializer.serialize(curState.value), curState.updateImportance)
    )
    write(serializedHistory)
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
  }


}