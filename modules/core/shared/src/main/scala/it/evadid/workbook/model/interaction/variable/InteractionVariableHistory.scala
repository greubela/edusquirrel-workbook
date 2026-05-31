package it.evadid.workbook.model.interaction.variable

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.sync.UpdateImportance.DEFAULT
import it.evadid.workbook.model.interaction.sync.{SyncStrategy, UpdateImportance}

import java.time.LocalDateTime
import scala.util.Try

case class InteractionVariableHistory[T](events: Set[InteractionVariableState[T]]) {

  lazy val lastState: InteractionVariableState[T] = events.maxBy(_.timestamp)

  def cleanedHistory(): InteractionVariableHistory[T] = {
    val nonDefault = events.filter(_.updateImportance != DEFAULT)
    if(nonDefault.nonEmpty) InteractionVariableHistory(nonDefault)
    else InteractionVariableHistory(Set(lastState))
  }

  def serialized(serializer: Serializer[T]): InteractionVariableHistorySerialized = {
    InteractionVariableHistorySerialized(events.map(_.serialized(serializer)))
  }

  def serializedWithStrategy(syncStrategy: SyncStrategy, serializer: Serializer[T]): InteractionVariableHistorySerialized = {
    val syncEvents = syncStrategy.selectEventsToSync(events)
    InteractionVariableHistory(syncEvents).cleanedHistory().serialized(serializer)
  }

  def withAddedEvents(newEvents: Set[InteractionVariableState[T]]): InteractionVariableHistory[T] = {
    InteractionVariableHistory(events ++ newEvents).cleanedHistory()
  }

  def withAddedEvent(event: InteractionVariableState[T]): InteractionVariableHistory[T] = {
    withAddedEvents(Set(event))
  }

  def withAddedEvents(newEvents: InteractionVariableHistory[T]): InteractionVariableHistory[T] = {
    withAddedEvents(newEvents.events)
  }

  def withAddedEvents(serializer: Serializer[T], serializedHistory: InteractionVariableHistorySerialized): InteractionVariableHistory[T] = {
    try {
      withAddedEvents(serializedHistory.deserialize(serializer))
    } catch {
      case e: Throwable => this
    }
  }
  
  


}

/*
case class InteractionVariableStorage[T](keyForSerialization: String, history: List[InteractionVariableState[T]], io: Serializer[T]) {

  lazy val serialized: InteractionHistorySerialized = InteractionHistorySerialized(keyForSerialization, history.map(_.serialized(io)))



  def withDeserializedHistory(deserializedHistory: InteractionHistorySerialized): InteractionVariableStorage[T] =
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
*/