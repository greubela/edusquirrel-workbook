package it.evadid.workbook.interaction.variable

import it.evadid.core.util.InfoUtil
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.grading.GradedState
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingResult
import it.evadid.workbook.interaction.sync.SyncStrategy
import it.evadid.workbook.interaction.sync.UpdateImportance.DEFAULT
import upickle.default.*


case class InteractionVariableHistory[T, GA, SA]
(
  events: Set[InteractionVariableState[T]],
  gradings: Map[T, GradedState[T, GA]],
  scaffoldings: Map[T, ScaffoldingResult[T, SA]]
) {

  def lastStateOption: Option[InteractionVariableState[T]] = events.maxByOption(_.timestamp)

  def cleanedHistory(): InteractionVariableHistory[T, GA, SA] = {
    val nonDefault = events.filter(_.updateImportance != DEFAULT)
    if (nonDefault.nonEmpty) this.copy(events = nonDefault)
    else this.copy(events = lastStateOption.toSet)
  }

  def updateEvents(eventFunc: Set[InteractionVariableState[T]] => Set[InteractionVariableState[T]]): InteractionVariableHistory[T, GA, SA] = {
    this.copy(events = eventFunc(events))
  }

  def updateGradings(gradingFunc: Map[T, GradedState[T, GA]] => Map[T, GradedState[T, GA]]): InteractionVariableHistory[T, GA, SA] = {
    this.copy(gradings = gradingFunc(gradings))
  }

  def updateScaffoldings(scaffoldingFunc: Map[T, ScaffoldingResult[T, SA]] => Map[T, ScaffoldingResult[T, SA]]): InteractionVariableHistory[T, GA, SA] = {
    this.copy(scaffoldings = scaffoldingFunc(scaffoldings))
  }

  def filterWithStrategy(syncStrategy: SyncStrategy): InteractionVariableHistory[T, GA, SA] = {
    updateEvents(eventFunc = syncStrategy.selectEventsToSync)
  }


  def withAddedEvents(newEvents: Set[InteractionVariableState[T]]): InteractionVariableHistory[T, GA, SA] = {
    updateEvents(curEvents => (curEvents ++ newEvents)).cleanedHistory()
  }

  def withAddedEvent(event: InteractionVariableState[T]): InteractionVariableHistory[T, GA, SA] = {
    withAddedEvents(Set(event))
  }

  override val toString: String = s"InteractionVariableHistory(${events.size} events, latest: ${InfoUtil.datetimeFormattedForLog(lastStateOption.map(_.timestamp))})"

}

object InteractionVariableHistory {

  given [T: ReadWriter, GA: ReadWriter, SA: ReadWriter]: ReadWriter[InteractionVariableHistory[T, GA, SA]] = macroRW

  type InteractionVariableHistoryUntyped = InteractionVariableHistory[?, ?, ?]

  def empty[T, GA, SA]: InteractionVariableHistory[T, GA, SA] = InteractionVariableHistory(Set(), Map(), Map())

}
