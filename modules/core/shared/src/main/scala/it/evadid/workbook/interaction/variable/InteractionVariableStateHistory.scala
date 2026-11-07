package it.evadid.workbook.interaction.variable

import it.evadid.core.util.InfoUtil
import it.evadid.workbook.interaction.sync.SyncStrategy
import upickle.default.*
import it.evadid.workbook.interaction.sync.UpdateImportance.*
import java.time.LocalDateTime


case class InteractionVariableStateHistory[T]
(
  events: Set[InteractionVariableState[T]]
) {

  def lastStateOption: Option[InteractionVariableState[T]] = events.maxByOption(_.timestamp)

  def latestChangeTimestamp: Option[LocalDateTime] = events.iterator.map(_.timestamp).maxOption

  def cleanedHistory(): InteractionVariableStateHistory[T] = {
    val nonDefault = events.filter(_.updateImportance != DEFAULT)
    if (nonDefault.nonEmpty) this.copy(events = nonDefault)
    else this.copy(events = lastStateOption.toSet)
  }

  def updateEvents(eventFunc: Set[InteractionVariableState[T]] => Set[InteractionVariableState[T]]): InteractionVariableStateHistory[T] = {
    this.copy(events = eventFunc(events))
  }

  def withAddedEvents(newEvents: Set[InteractionVariableState[T]]): InteractionVariableStateHistory[T] = {
    updateEvents(curEvents => (curEvents ++ newEvents)).cleanedHistory()
  }

  def withAddedEvent(event: InteractionVariableState[T]): InteractionVariableStateHistory[T] = {
    withAddedEvents(Set(event))
  }

  override val toString: String = s"InteractionVariableHistory(${events.size} events, latest: ${InfoUtil.datetimeFormattedForLog(lastStateOption.map(_.timestamp))})"

}

object InteractionVariableStateHistory {

  given [T: ReadWriter]: ReadWriter[InteractionVariableStateHistory[T]] = macroRW

  type InteractionVariableHistoryUntyped = InteractionVariableStateHistory[?]

}
