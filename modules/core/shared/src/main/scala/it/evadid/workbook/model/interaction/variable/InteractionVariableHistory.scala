package it.evadid.workbook.model.interaction.variable

import it.evadid.core.util.io.Serializer
import it.evadid.util.logging.Logger
import it.evadid.workbook.model.interaction.sync.SyncStrategy
import it.evadid.workbook.model.interaction.sync.UpdateImportance.DEFAULT

case class InteractionVariableHistory[T](events: Set[InteractionVariableState[T]]) {

  def lastStateOption: Option[InteractionVariableState[T]] = events.maxByOption(_.timestamp)

  def cleanedHistory(): InteractionVariableHistory[T] = {
    val nonDefault = events.filter(_.updateImportance != DEFAULT)
    if (nonDefault.nonEmpty) InteractionVariableHistory(nonDefault)
    else InteractionVariableHistory(lastStateOption.toSet)
  }

  def map(func: Set[InteractionVariableState[T]] => Set[InteractionVariableState[T]]): InteractionVariableHistory[T] = {
    InteractionVariableHistory(func(events.toSet))
  }

  def serialized(serializer: Serializer[T]): InteractionVariableHistorySerialized = {
    InteractionVariableHistorySerialized(events.map(_.serialized(serializer)))
  }

  def serializedWithStrategy(syncStrategy: SyncStrategy, serializer: Serializer[T]): InteractionVariableHistorySerialized = {
    val historyToSync: InteractionVariableHistory[T] = syncStrategy.selectEventsToSync(this)
    historyToSync.cleanedHistory().serialized(serializer)
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

  def withAddedEvents(logger: Logger, serializer: Serializer[T], serializedHistory: InteractionVariableHistorySerialized): InteractionVariableHistory[T] = {
    val (success, failed) = serializedHistory.tryDeserialize(serializer)
    if (failed.states.nonEmpty) logger.logWarn("Could not deserialize " + failed.states.size + " states in InteractionVariableHistory::withAddedEvents!")
    withAddedEvents(success)
  }


}

object InteractionVariableHistory {

  def empty[T]: InteractionVariableHistory[T] = InteractionVariableHistory(Set())

}
