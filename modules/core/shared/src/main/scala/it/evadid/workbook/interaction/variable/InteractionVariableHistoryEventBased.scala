package it.evadid.workbook.interaction.variable

import it.evadid.workbook.abstractions.grading.{GradedState, GradingResult, GradingStatus, WorkbookInteractionGrader}
import it.evadid.workbook.interaction.sync.{EventSyncStrategy, UpdateImportance}
import it.evadid.workbook.interaction.variable.events.*
import java.time.LocalDateTime
import upickle.default.*

/** An additional history representation; the existing interaction and synchronization paths do not use it yet. */
case class InteractionVariableHistoryEventBased[T, GA, SA](events: InteractionEvents[T, GA, SA]) derives ReadWriter {
  lazy val allStates: Set[AnswerChangedEvent[T, GA, SA]] = events.values.collect {
    case event: AnswerChangedEvent[T, GA, SA] => event
  }
  lazy val gradingEvents: Set[GradingEvent[T, GA, SA]] = events.values.collect {
    case event: GradingEvent[T, GA, SA] => event
  }
  lazy val scaffoldingEvents: Set[ScaffoldingEvent[T, GA, SA]] = events.values.collect {
    case event: ScaffoldingEvent[T, GA, SA] => event
  }

  /** Project the latest lifecycle event per configured grader identity. Completion wins a timestamp tie. */
  def gradingForState(knownGraders: Seq[WorkbookInteractionGrader[T, GA]], state: T): GradedState[T, GA] = {
    val relevant = gradingEvents.filter(_.associatedState == state).groupBy(_.grader.key)
    val results = knownGraders.distinctBy(_.key).map { grader =>
      val candidates = relevant.getOrElse(grader.key, Set.empty)
      val latestTime = candidates.map(_.timestamp).maxOption
      val latest = candidates.filter(event => latestTime.contains(event.timestamp))
      val completed = latest.collect { case event: GradingCompletedEvent[T, GA, SA] =>
        event.result.copy(grader = grader)
      }
      val result = completed.reduceOption(_.merge(_)).getOrElse(GradingResult(
        grader, state,
        if latest.nonEmpty then GradingStatus.NotFinished() else GradingStatus.NotRequested(),
        None, None, latestTime.getOrElse(LocalDateTime.MIN)))
      grader -> result
    }.toMap
    GradedState(state, results)
  }

  def cleanWithStrategy(syncStrategy: EventSyncStrategy): InteractionVariableHistoryEventBased[T, GA, SA] =
    copy(events = InteractionEvents(syncStrategy.selectEvents(events.values)))

  /** Persistence cleanup only: no answer, grading or scaffolding events are synthesized. */
  def clean(): InteractionVariableHistoryEventBased[T, GA, SA] =
    copy(events = InteractionEvents(events.values.filter(_.updateImportance != UpdateImportance.TEMPORARY)))
}
