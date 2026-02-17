package workbook.model.exercise

import com.raquo.airstream.state.Var
import contentmanagement.model.chat.MessengerModel
import util.Serializer
import workbook.model.exercise.InteractionVariable.{ExerciseVariableState, UpdateImportance, deserializeHistory}
import workbook.model.history.sync.{LocalStorageSync, SyncInformation, SyncStrategy}
import upickle.default.*

case class InteractionVariable[T](underlyingExercise: ExerciseContent, initHistory: List[ExerciseVariableState[T]], initSyncInformation: List[SyncInformation], io: Serializer[T]) {

  assert(initHistory.nonEmpty, "ExerciseVariable: initHistory must not be empty (must provide history with default state!)")

  private var history: List[ExerciseVariableState[T]] = initHistory
  private var syncSources: List[SyncInformation] = initSyncInformation

  val asVar: Var[T] = Var[T](initHistory.maxBy(_.epochTimestampMillis).value)

  def updateStateFromUserInteraction(newValue: T, epochTimestampMillis: Long, updateSize: UpdateImportance): Unit = {
    val newState = ExerciseVariableState[T](newValue, epochTimestampMillis, updateSize)
    history = history ++ List(newState)
    asVar.update(_ => newValue)
    syncToAll()
    //println("updated history at " + epochTimestampMillis + " (" + updateSize + "): " + newState)
  }

  private def updateVarFromHistory(): Unit = {
    val setValue = history.maxBy(_.epochTimestampMillis).value
    if (asVar.now() != setValue) {
      asVar.update(_ => setValue)
      //println("[INFO] Set current var state to: " + setValue)
    } else {
      //println("val not changed (" + asVar.now() + ") from history (" + setValue + ")")
    }
  }


  private val keyForSerialization: String = underlyingExercise.id + "_history"

  def syncToAll(): Unit = {
    syncSources.foreach(syncInfo => {
      val eventsToSync = syncInfo.syncStrategy.selectEventsToSync(history)
      syncInfo.syncSource.syncTo(keyForSerialization, InteractionVariable.serializeHistory(eventsToSync, io))
    })

    //println("[INFO] synced to " + syncSources.size + " sources")
  }

  def syncFromAll(): Unit = {
    val eventCount = history.size

    syncSources.foreach(syncInfo => {
      val eventStr = syncInfo.syncSource.syncKeyFrom(keyForSerialization)
      if (eventStr.nonEmpty) {
        val addToHistory = InteractionVariable.deserializeHistory(eventStr.get, io)
        val newHistory = (addToHistory.toSet ++ history.toSet).toList.sortBy(_.epochTimestampMillis)
        history = newHistory
      }
    })

    val withoutDefault = history.filter(_.updateImportance != UpdateImportance.DEFAULT)
    if (withoutDefault.nonEmpty) {
      history = withoutDefault
    }
    //println("[INFO] synced from all sources, added " + (history.size - eventCount) + " events")

    updateVarFromHistory()
  }

}

object InteractionVariable {

  def messengerVariable(exerciseContent: ExerciseContent, initMessages: MessengerModel): InteractionVariable[MessengerModel] = {
    val io: Serializer[MessengerModel] = new Serializer[MessengerModel] {
      override def serialize(obj: MessengerModel): String = obj.toJson

      override def deserialize(str: String): MessengerModel = MessengerModel.fromJson(str)
    }
    InteractionVariable[MessengerModel](exerciseContent,
      List(ExerciseVariableState(initMessages, System.currentTimeMillis(), UpdateImportance.DEFAULT)),
      List(SyncInformation(LocalStorageSync, SyncStrategy.SYNC_EVERYTHING)),
      io)
  }


  def stringVariable(exerciseContent: ExerciseContent, defaultValue: String): InteractionVariable[String] = {
    val io = new Serializer[String] {
      override def serialize(obj: String): String = obj

      override def deserialize(serialized: String): String = serialized
    }
    InteractionVariable(exerciseContent,
      List(ExerciseVariableState(defaultValue, System.currentTimeMillis(), UpdateImportance.DEFAULT)),
      List(SyncInformation(LocalStorageSync, SyncStrategy.SYNC_EVERYTHING)),
      io)
  }

  def apply[T](exerciseContent: ExerciseContent, defaultValue: T, io: Serializer[T]): InteractionVariable[T] =
    InteractionVariable(exerciseContent, List(ExerciseVariableState(defaultValue, System.currentTimeMillis(), UpdateImportance.DEFAULT)), List(), io)


  private case class SerializedExerciseVariableState(
    epochTimestampMillis: Long,
    serializedValue: String,
    updateImportance: UpdateImportance
  )

  private given ReadWriter[UpdateImportance] = readwriter[String].bimap[UpdateImportance](_.toString, UpdateImportance.valueOf)
  private given ReadWriter[SerializedExerciseVariableState] = macroRW

  private def serializeHistory[T](history: List[ExerciseVariableState[T]], serializer: Serializer[T]): String = {
    val serializedHistory = history.map(curState =>
      SerializedExerciseVariableState(curState.epochTimestampMillis, serializer.serialize(curState.value), curState.updateImportance)
    )
    write(serializedHistory)
  }

  private def deserializeHistory[T](serializedHistory: String, serializer: Serializer[T]): Set[ExerciseVariableState[T]] = {
    val deserialized = read[List[SerializedExerciseVariableState]](serializedHistory)
    deserialized.map(curState =>
      ExerciseVariableState(
        serializer.deserialize(curState.serializedValue),
        curState.epochTimestampMillis,
        curState.updateImportance
      )
    ).toSet
  }


  case class ExerciseVariableState[T](value: T, epochTimestampMillis: Long, updateImportance: UpdateImportance)

  enum UpdateImportance {
    case DEFAULT // default value for new variables
    case TEMPORARY // keep until the next real event comes
    case MINOR // keep until the next major event comes
    case MAJOR // always keep in history
  }

}