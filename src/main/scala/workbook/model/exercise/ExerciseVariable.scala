package workbook.model.exercise

import com.raquo.airstream.state.Var
import util.Serializer
import workbook.model.exercise.ExerciseVariable.{ExerciseVariableState, UpdateImportance, deserializeHistory}
import workbook.model.history.sync.{LocalStorageSync, SyncInformation, SyncStrategy}

case class ExerciseVariable[T](underlyingExercise: ExerciseContent, initHistory: List[ExerciseVariableState[T]], initSyncInformation: List[SyncInformation], io: Serializer[T]) {

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
    if(asVar.now() != setValue){
      asVar.update(_ => setValue)
      //println("[INFO] Set current var state to: " + setValue)
    }else{
      //println("val not changed (" + asVar.now() + ") from history (" + setValue + ")")
    }
  }


  private val keyForSerialization: String = underlyingExercise.id + "_history"

  def syncToAll(): Unit = {
    syncSources.foreach(syncInfo => {
      val eventsToSync = syncInfo.syncStrategy.selectEventsToSync(history)
      syncInfo.syncSource.syncTo(keyForSerialization, ExerciseVariable.serializeHistory(eventsToSync, io))
    })

    //println("[INFO] synced to " + syncSources.size + " sources")
  }

  def syncFromAll(): Unit = {
    val eventCount = history.size

    syncSources.foreach(syncInfo => {
      val eventStr = syncInfo.syncSource.syncKeyFrom(keyForSerialization)
      if (eventStr.nonEmpty) {
        val addToHistory = ExerciseVariable.deserializeHistory(eventStr.get, io)
        val newHistory = (addToHistory.toSet ++ history.toSet).toList.sortBy(_.epochTimestampMillis)
        history = newHistory
      }
    })

    val withoutDefault = history.filter(_.updateImportance != UpdateImportance.DEFAULT)
    if(withoutDefault.nonEmpty){
      history = withoutDefault
    }
    //println("[INFO] synced from all sources, added " + (history.size - eventCount) + " events")

    updateVarFromHistory()
  }

}

object ExerciseVariable {

  def stringVariable(exerciseContent: ExerciseContent, defaultValue: String): ExerciseVariable[String] = {
    val io = new Serializer[String] {
      override def serialize(obj: String): String = obj

      override def deserialize(serialized: String): String = serialized
    }
    ExerciseVariable(exerciseContent,
      List(ExerciseVariableState(defaultValue, System.currentTimeMillis(), UpdateImportance.DEFAULT)),
      List(SyncInformation(LocalStorageSync, SyncStrategy.SYNC_EVERYTHING)),
      io)
  }

  def apply[T](exerciseContent: ExerciseContent, defaultValue: T, io: Serializer[T]): ExerciseVariable[T] =
    ExerciseVariable(exerciseContent, List(ExerciseVariableState(defaultValue, System.currentTimeMillis(), UpdateImportance.DEFAULT)), List(), io)


  private def serializeHistory[T](history: List[ExerciseVariableState[T]], serializer: Serializer[T]): String = {
    val res = new StringBuilder("")
    for (curState <- history) {
      res.append(curState.epochTimestampMillis.toString + ";;;")
      res.append(serializer.serialize(curState.value) + ";;;")
      res.append(curState.updateImportance.toString + ";;;;;")
    }
    res.toString()
  }

  private def deserializeHistory[T](serializedHistory: String, serializer: Serializer[T]): Set[ExerciseVariableState[T]] = {
    val res = serializedHistory.split(";;;;;").flatMap(curEvent => {
      //println("try to parse event: " + curEvent)
      val parts = curEvent.split(";;;")
      if(parts.size == 3) {
        val timestamp = parts(0).toLong
        val value = serializer.deserialize(parts(1))
        val importance = UpdateImportance.valueOf(parts(2))
        Option(ExerciseVariableState[T](value, timestamp, importance))
      }else{
        None
      }
    })
    res.toSet
  }


  case class ExerciseVariableState[T](value: T, epochTimestampMillis: Long, updateImportance: UpdateImportance)

  enum UpdateImportance {
    case DEFAULT // default value for new variables
    case TEMPORARY // keep until the next real event comes
    case MINOR // keep until the next major event comes
    case MAJOR // always keep in history
  }

}