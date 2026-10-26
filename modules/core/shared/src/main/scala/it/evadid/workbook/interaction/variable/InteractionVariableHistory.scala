package it.evadid.workbook.interaction.variable

import it.evadid.core.util.InfoUtil
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.grading.GradedState
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingResult
import it.evadid.workbook.interaction.sync.SyncStrategy
import it.evadid.workbook.interaction.sync.UpdateImportance.DEFAULT
import upickle.default.*
import java.time.LocalDateTime


case class InteractionVariableHistory[T, GA, SA]
(
  events: Set[InteractionVariableState[T]],
  gradings: Map[T, GradedState[T, GA]],
  scaffoldings: Map[T, ScaffoldingResult[T, SA]]
) {

  def lastStateOption: Option[InteractionVariableState[T]] = events.maxByOption(_.timestamp)

  def latestChangeTimestamp: Option[LocalDateTime] =
    (events.iterator.map(_.timestamp) ++ gradings.valuesIterator.flatMap(_.testsWithResults.iterator.map(_.gradingTime)) ++
      scaffoldings.valuesIterator.map(_.scaffoldingTime)).maxOption

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

  def mergeWith(histories: Iterable[InteractionVariableHistory[T, GA, SA]]): InteractionVariableHistory[T, GA, SA] = {
    val all = this +: histories.toVector
    val restoredGradings = all.flatMap(_.gradings).groupMap(_._1)(_._2).flatMap { (state, snapshots) =>
      val results = snapshots.filter(_.state == state).flatMap(_.testsWithResults).filter(_.gradedState == state)
      Option.when(results.nonEmpty) {
        val latest = results.groupBy(_.grader).values.flatMap { sameGrader =>
          val time = sameGrader.map(_.gradingTime).max
          sameGrader.filter(_.gradingTime == time)
        }.toSet
        state -> GradedState(state, latest)
      }
    }
    val restoredHints = all.flatMap(_.scaffoldings).filter((state, hint) => hint.forState == state)
      .groupMap(_._1)(_._2).flatMap { (state, candidates) =>
        candidates.distinct match {
          case Vector(single) => Some(state -> single)
          case _ => None
        }
      }
    copy(events = all.flatMap(_.events).toSet, gradings = restoredGradings,
      scaffoldings = restoredHints ++ scaffoldings.filter((state, hint) => hint.forState == state)).cleanedHistory()
  }

  override val toString: String = s"InteractionVariableHistory(${events.size} events, latest: ${InfoUtil.datetimeFormattedForLog(lastStateOption.map(_.timestamp))})"

}

object InteractionVariableHistory {

  given [T: ReadWriter, GA: ReadWriter, SA: ReadWriter]: ReadWriter[InteractionVariableHistory[T, GA, SA]] = macroRW

  type InteractionVariableHistoryUntyped = InteractionVariableHistory[?, ?, ?]

  def empty[T, GA, SA]: InteractionVariableHistory[T, GA, SA] = InteractionVariableHistory(Set(), Map(), Map())

}
