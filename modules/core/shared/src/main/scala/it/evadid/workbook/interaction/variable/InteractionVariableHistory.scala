package it.evadid.workbook.interaction.variable

import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.abstractions.grading.{GradedState, GradingResult, GradingStatus, WorkbookInteractionGrader}
import it.evadid.workbook.interaction.sync.{EventSyncStrategy, UpdateImportance}
import it.evadid.workbook.interaction.variable.events.*
import java.time.LocalDateTime
import upickle.default.*

/** Canonical interaction history: all saved state and feedback live in timestamped events. */
case class InteractionVariableHistory[T, GA, SA](events: InteractionEvents[T, GA, SA]) derives ReadWriter {
  lazy val allStates: Set[AnswerChangedEvent[T, GA, SA]] = events.values.collect {
    case event: AnswerChangedEvent[T, GA, SA] => event
  }
  lazy val gradingEvents: Set[GradingEvent[T, GA, SA]] = events.values.collect {
    case event: GradingEvent[T, GA, SA] => event
  }
  lazy val scaffoldingEvents: Set[ScaffoldingEvent[T, GA, SA]] = events.values.collect {
    case event: ScaffoldingEvent[T, GA, SA] => event
  }

  lazy val historyStates: InteractionVariableStateHistory[T] = InteractionVariableStateHistory(
    allStates.map(event => InteractionVariableState(event.newState, event.updateImportance, event.timestamp)))
  lazy val historyGrading: InteractionVariableGradingHistory[T, GA] = InteractionVariableGradingHistory.fromResults(
    gradingEvents.collect { case event: GradingCompletedEvent[T, GA, SA] => event.result })
  lazy val historyScaffolding: InteractionVariableScaffoldingHistory[T, SA] = InteractionVariableScaffoldingHistory(
    scaffoldingEvents.collect { case event: ScaffoldingCompletedEvent[T, GA, SA] => event.result })
  def timestampLastChanged: Option[LocalDateTime] = events.timestampLastChanged
  def size: Int = events.values.size

  def addEvent(event: InteractionEvent[T, GA, SA]): InteractionVariableHistory[T, GA, SA] =
    copy(events = InteractionEvents(events.values + event))
  def addState(state: InteractionVariableState[T]): InteractionVariableHistory[T, GA, SA] =
    addEvent(AnswerChangedEvent(state.value, state.timestamp, state.updateImportance))
  def addStates(states: Iterable[InteractionVariableState[T]]): InteractionVariableHistory[T, GA, SA] =
    states.foldLeft(this)(_.addState(_))
  def addGrading(result: GradingResult[T, GA]): InteractionVariableHistory[T, GA, SA] = result.gradingStatus match {
    case _: GradingStatus.NotRequested | _: GradingStatus.NotFinished => this
    case _ => addEvent(GradingCompletedEvent(result))
  }
  def addGrading(result: GradedState[T, GA]): InteractionVariableHistory[T, GA, SA] =
    result.graderResultMap.values.foldLeft(this)(_.addGrading(_))
  def addGradings(results: Iterable[GradingResult[T, GA]]): InteractionVariableHistory[T, GA, SA] =
    results.foldLeft(this)(_.addGrading(_))
  def addScaffolding(result: ScaffoldingResult[T, SA]): InteractionVariableHistory[T, GA, SA] = result.scaffoldingStatus match {
    case _: ScaffoldingStatus.ScaffoldingRequestedButNotFinished =>
      addEvent(ScaffoldingStartedEvent(result.forState, result.scaffoldingTime, result.scaffoldingEntity))
    case _ => addEvent(ScaffoldingCompletedEvent(result))
  }
  def addScaffoldings(results: Iterable[ScaffoldingResult[T, SA]]): InteractionVariableHistory[T, GA, SA] =
    results.foldLeft(this)(_.addScaffolding(_))

  /** Interactive normalization removes superseded default answers, without dropping independent delta feedback. */
  def normalize(): InteractionVariableHistory[T, GA, SA] = {
    val states = historyStates.cleanedHistory().events
    val obsolete = allStates.filterNot(event => states(InteractionVariableState(event.newState, event.updateImportance, event.timestamp)))
    val obsoleteValues = obsolete.map(_.associatedState) -- states.map(_.value)
    copy(events = InteractionEvents(events.values.filterNot(event => obsoleteValues(event.associatedState)) -- obsolete))
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

  def mergeWith(other: InteractionVariableHistory[T, GA, SA]): InteractionVariableHistory[T, GA, SA] =
    copy(events = events.merge(other.events))

  def mergeWith(others: Seq[InteractionVariableHistory[T, GA, SA]]): InteractionVariableHistory[T, GA, SA] =
    others.foldLeft(this)(_.mergeWith(_))

  def cleanWithStrategy(syncStrategy: EventSyncStrategy): InteractionVariableHistory[T, GA, SA] =
    copy(events = InteractionEvents(syncStrategy.selectEvents(events.values)))

  /** Persistence cleanup only: no answer, grading or scaffolding events are synthesized. */
  def clean(): InteractionVariableHistory[T, GA, SA] =
    copy(events = InteractionEvents(events.values.filter(_.updateImportance != UpdateImportance.TEMPORARY)))
}

object InteractionVariableHistory {
  def empty[T, GA, SA]: InteractionVariableHistory[T, GA, SA] = InteractionVariableHistory(InteractionEvents.empty)

  /** Build events from typed answers and completed results; this does not decode an older saved schema. */
  def fromResults[T, GA, SA](states: Set[InteractionVariableState[T]],
      gradings: Set[GradedState[T, GA]] = Set.empty[GradedState[T, GA]], scaffoldings: Set[ScaffoldingResult[T, SA]] = Set.empty[ScaffoldingResult[T, SA]]): InteractionVariableHistory[T, GA, SA] =
    gradings.foldLeft(empty[T, GA, SA].addStates(states))(_.addGrading(_)).addScaffoldings(scaffoldings)

  def fromResults[T, GA, SA](states: InteractionVariableStateHistory[T], gradings: InteractionVariableGradingHistory[T, GA],
      scaffoldings: InteractionVariableScaffoldingHistory[T, SA]): InteractionVariableHistory[T, GA, SA] =
    fromResults(states.events, gradings.gradingsFinished, scaffoldings.scaffoldingsFinished)

  /** Backward-compatibility constructor for tests. Converts to event-based format. */
  def apply[T, GA, SA](states: InteractionVariableStateHistory[T],
      gradings: InteractionVariableGradingHistory[T, GA],
      scaffoldings: InteractionVariableScaffoldingHistory[T, SA]): InteractionVariableHistory[T, GA, SA] =
    fromResults(states, gradings, scaffoldings)

  /** Backward-compatibility constructor for tests. Converts to event-based format. */
  def apply[T, GA, SA](events: Set[InteractionVariableState[T]]): InteractionVariableHistory[T, GA, SA] =
    fromResults(events, Set.empty, Set.empty)
}
