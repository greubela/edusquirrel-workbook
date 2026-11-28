package it.evadid.workbook.history

import it.evadid.core.util.InfoUtil
import it.evadid.workbook.sync.EventSyncStrategy
import upickle.default.*
import it.evadid.workbook.sync.UpdateImportance.*
import java.time.LocalDateTime


case class InteractionVariableStateHistory[T]
(
  events: Set[InteractionVariableState[T]]
) {

  def lastStateOption: Option[InteractionVariableState[T]] = events.maxByOption(_.timestamp)

  def latestChangeTimestamp: Option[LocalDateTime] = events.iterator.map(_.timestamp).maxOption

  /** Drops DEFAULT answers once another answer exists and TEMPORARY answers once a later answer exists.
    * The latest answer always remains.
    */
  def cleanedHistory(): InteractionVariableStateHistory[T] = {
    val nonDefault = events.filter(_.updateImportance != DEFAULT)
    val candidates = if nonDefault.nonEmpty then nonDefault else lastStateOption.toSet
    val latest = candidates.iterator.map(_.timestamp).maxOption
    copy(events = candidates.filter(state => state.updateImportance != TEMPORARY || latest.contains(state.timestamp)))
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

  override lazy val toString: String = s"InteractionVariableHistory.fromResults(${events.size} events, latest: ${InfoUtil.datetimeFormattedForLog(lastStateOption.map(_.timestamp))})"

}

object InteractionVariableStateHistory {

  given [T: ReadWriter]: ReadWriter[InteractionVariableStateHistory[T]] = macroRW

  type InteractionVariableHistoryUntyped = InteractionVariableStateHistory[?]

}
