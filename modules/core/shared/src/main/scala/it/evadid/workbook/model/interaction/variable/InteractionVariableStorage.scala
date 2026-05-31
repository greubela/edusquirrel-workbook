package it.evadid.workbook.model.interaction.variable

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.sync.{SyncInformation, SyncStrategy, UpdateImportance}

import java.time.LocalDateTime
import scala.collection.mutable


case class InteractionVariableStorage[T](keyForSerialization: String, history: List[InteractionVariableState[T]], io: Serializer[T]) {

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

  def afterReset(defaultValue: T): InteractionVariableStorage[T] = {
    InteractionVariableStorage(keyForSerialization, defaultValue, io)
  }

  def withAdditionalStates(additionalStates: List[InteractionVariableState[T]]): InteractionVariableStorage[T] = {
    val res = this.copy(history = history ++ additionalStates).withCleanedDefaultStates()
    val last1 = this.lastState
    val last2 = res.lastState
    //if (last1 != last2 || keyForSerialization == "auto-id-1_history") println("changed history: " + last1.serialized(io) + " -> " + last2.serialized(io))
    res
  }

  def withCleanedDefaultStates(): InteractionVariableStorage[T] = {
    if (!history.exists(_.updateImportance == UpdateImportance.DEFAULT) || !history.exists(_.updateImportance != UpdateImportance.DEFAULT)) {
      this
    } else {
      this.copy(history = history.filter(_.updateImportance != UpdateImportance.DEFAULT))
    }
  }


}

private object InteractionVariableStorage {
  def apply[T](syncId: String, defaultValue: T, io: Serializer[T]): InteractionVariableStorage[T] = {
    val history: List[InteractionVariableState[T]] = List(InteractionVariableState[T](defaultValue, UpdateImportance.DEFAULT, LocalDateTime.now()))
    InteractionVariableStorage[T](syncId, history, io)
  }
}
