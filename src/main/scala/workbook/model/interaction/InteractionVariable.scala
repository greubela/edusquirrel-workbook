package workbook.model.interaction



import util.Serializer
import com.raquo.airstream.ownership.Owner
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import upickle.default.*
import workbook.model.abstractions.WorkbookInteraction
import workbook.model.interaction.history.*
import workbook.model.interaction.history.UpdateImportance.DEFAULT
import workbook.model.interaction.sync.*

case class InteractionVariable[T](underlyingInteraction: WorkbookInteraction[T], initHistory: List[InteractionVariableState[T]], initSyncInformation: List[SyncInformation], io: Serializer[T]) {

  assert(initHistory.nonEmpty, "ExerciseVariable: initHistory must not be empty (must provide history with default state!)")

  private var history: List[InteractionVariableState[T]] = initHistory
  private var syncSources: List[SyncInformation] = initSyncInformation

  private val underlyingVar: Var[T] = Var[T](initHistory.maxBy(_.epochTimestampMillis).value)

  def interactionSignal: StrictSignal[T] = underlyingVar.signal

  def lastUpdate: InteractionVariableState[T] = history.maxBy(_.epochTimestampMillis) 
  
  def currentValue: T = underlyingVar.now()

  def createBoundVarWithUpdateImportance(importance: UpdateImportance): Var[T] = {
    val outerVar = Var[T](underlyingVar.now())
    outerVar.signal.foreach(newValue => updateStateFromUserInteraction(newValue, System.currentTimeMillis(), importance))
    underlyingVar.signal.foreach(newValue => if (newValue != outerVar.now()) outerVar.update(_ => newValue))
    outerVar
  }

  def updateStateFromUserInteraction(newValue: T, epochTimestampMillis: Long, updateSize: UpdateImportance): Unit = {
    if (newValue != underlyingVar.now()) {
      val newState = InteractionVariableState[T](newValue, epochTimestampMillis, updateSize)
      history = history ++ List(newState)
      underlyingVar.update(_ => newValue)
      syncToAll()
      //println("updated history at " + epochTimestampMillis + " (" + updateSize + "): " + newState)
    }
  }

  private def updateVarFromHistory(): Unit = {
    val setValue = history.maxBy(_.epochTimestampMillis).value
    if (underlyingVar.now() != setValue) {
      underlyingVar.update(_ => setValue)
      //println("[INFO] Set current var state to: " + setValue)
    } else {
      //println("val not changed (" + asVar.now() + ") from history (" + setValue + ")")
    }
  }

  private val keyForSerialization: String = underlyingInteraction.id + "_history"

  def syncToAll(): Unit = {
    if(history.exists(_.updateImportance != DEFAULT)) {
      syncSources.foreach(syncInfo => {
        val eventsToSync = syncInfo.syncStrategy.selectEventsToSync(history)
        syncInfo.syncSource.syncTo(keyForSerialization, InteractionVariable.serializeHistory(eventsToSync, io))
      })
      //println("[INFO] history '" + keyForSerialization + "' changed, synced to " + syncSources.size + " sources")//, current value: \n" + io.serialize(underlyingVar.now()) + ")")
    }

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
    //println("[INFO] synced history '" + keyForSerialization + "' from all sources, added " + (history.size - eventCount) + " events")//, current value: \n" + io.serialize(underlyingVar.now()))

    updateVarFromHistory()
  }

  implicit val appOwner: Owner = unsafeWindowOwner
  syncFromAll()
}

object InteractionVariable {


  def stringVariable(interaction: WorkbookInteraction[String], defaultValue: String): InteractionVariable[String] = {
    val io = new Serializer[String] {
      override def serialize(obj: String): String = obj

      override def deserialize(serialized: String): String = serialized
    }
    InteractionVariable(interaction,
      List(InteractionVariableState(defaultValue, System.currentTimeMillis(), UpdateImportance.DEFAULT)),
      interaction.workbookInfoVar.now().config.getSyncDestinations(),
      io)
  }

  def apply[T](interaction: WorkbookInteraction[T], defaultValue: T, io: Serializer[T]): InteractionVariable[T] =
    InteractionVariable(
      interaction,
      List(InteractionVariableState(defaultValue, System.currentTimeMillis(), UpdateImportance.DEFAULT)),
      interaction.workbookInfoVar.now().config.getSyncDestinations(),
      io)


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