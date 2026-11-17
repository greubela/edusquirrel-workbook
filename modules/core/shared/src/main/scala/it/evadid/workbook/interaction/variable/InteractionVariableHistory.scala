package it.evadid.workbook.interaction.variable

import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.abstractions.grading.{GradedState, GradingResult, GradingStatus, WorkbookInteractionGrader}
import it.evadid.workbook.interaction.sync.{EventSyncStrategy, UpdateImportance}
import it.evadid.workbook.interaction.variable.events.*

import java.time.LocalDateTime
import upickle.default.*

/** Type alias for compatibility - use 3 concrete types for serialization */
type InteractionVariableHistory3[F <: InteractionFormat] = InteractionVariableHistory[
  F, 
  F#InteractionStateType, 
  F#GradingInfoType, 
  F#ScaffoldingInfoType
]

/** Canonical interaction history: all saved state and feedback live in timestamped events. */
case class InteractionVariableHistory[
  F <: InteractionFormat,
  S <: F#InteractionStateType,
  G <: F#GradingInfoType,
  A <: F#ScaffoldingInfoType
](events: InteractionEvents[F]) {
  type InteractionStateType = S
  type GradingInfoType = G
  type ScaffoldingInfoType = A
  type Format = F
  lazy val allStates: Set[AnswerChangedEvent[F]] = events.values.collect {
    case event: AnswerChangedEvent[F] => event
  }
  lazy val gradingEvents: Set[GradingEvent[F]] = events.values.collect {
    case event: GradingEvent[F] => event
  }
  lazy val scaffoldingEvents: Set[ScaffoldingEvent[F]] = events.values.collect {
    case event: ScaffoldingEvent[F] => event
  }

  lazy val historyStates: InteractionVariableStateHistory[F#InteractionStateType] = InteractionVariableStateHistory(
    allStates.map(event => InteractionVariableState(event.newState, event.updateImportance, event.timestamp)))
  lazy val historyGrading: InteractionVariableGradingHistory[F#InteractionStateType, F#GradingInfoType] = InteractionVariableGradingHistory.fromResults(
    gradingEvents.collect { case event: GradingCompletedEvent[F] => event.result })
  lazy val historyScaffolding: InteractionVariableScaffoldingHistory[F#InteractionStateType, F#ScaffoldingInfoType] = InteractionVariableScaffoldingHistory(
    scaffoldingEvents.collect { case event: ScaffoldingCompletedEvent[F] => event.result })
  def timestampLastChanged: Option[LocalDateTime] = events.timestampLastChanged
  def size: Int = events.values.size

  def addEvent(event: InteractionEvent[F]): InteractionVariableHistory[F] =
    copy(events = InteractionEvents(events.values + event))
  def addState(state: InteractionVariableState[F#InteractionStateType]): InteractionVariableHistory[F] =
    addEvent(AnswerChangedEvent(state.value, state.timestamp, state.updateImportance))
  def addStates(states: Iterable[InteractionVariableState[F#InteractionStateType]]): InteractionVariableHistory[F] =
    states.foldLeft(this)(_.addState(_))
  def addGrading(result: GradingResult[F#InteractionStateType, F#GradingInfoType]): InteractionVariableHistory[F] = result.gradingStatus match {
    case _: GradingStatus.NotRequested | _: GradingStatus.NotFinished => this
    case _ => addEvent(GradingCompletedEvent(result))
  }
  def addGrading(result: GradedState[F#InteractionStateType, F#GradingInfoType]): InteractionVariableHistory[F] =
    result.graderResultMap.values.foldLeft(this)(_.addGrading(_))
  def addGradings(results: Iterable[GradingResult[F#InteractionStateType, F#GradingInfoType]]): InteractionVariableHistory[F] =
    results.foldLeft(this)(_.addGrading(_))
  def addScaffolding(result: ScaffoldingResult[F#InteractionStateType, F#ScaffoldingInfoType]): InteractionVariableHistory[F] = result.scaffoldingStatus match {
    case _: ScaffoldingStatus.ScaffoldingRequestedButNotFinished =>
      addEvent(ScaffoldingStartedEvent(result.forState, result.scaffoldingTime, result.scaffoldingEntity))
    case _ => addEvent(ScaffoldingCompletedEvent(result))
  }
  def addScaffoldings(results: Iterable[ScaffoldingResult[F#InteractionStateType, F#ScaffoldingInfoType]]): InteractionVariableHistory[F] =
    results.foldLeft(this)(_.addScaffolding(_))

  /** Interactive normalization removes superseded default answers, without dropping independent delta feedback. */
  def normalize(): InteractionVariableHistory[F] = {
    val states = historyStates.cleanedHistory().events
    val obsolete = allStates.filterNot(event => states(InteractionVariableState(event.newState, event.updateImportance, event.timestamp)))
    val obsoleteValues = obsolete.map(_.associatedState) -- states.map(_.value)
    copy(events = InteractionEvents(events.values.filterNot(event => obsoleteValues(event.associatedState)) -- obsolete))
  }

  /** Project the latest lifecycle event per configured grader identity. Completion wins a timestamp tie. */
  def gradingForState(knownGraders: Seq[WorkbookInteractionGrader[F#InteractionStateType, F#GradingInfoType]], state: F#InteractionStateType): GradedState[F#InteractionStateType, F#GradingInfoType] = {
    val relevant = gradingEvents.filter(_.associatedState == state).groupBy(_.grader.key)
    val results = knownGraders.distinctBy(_.key).map { grader =>
      val candidates = relevant.getOrElse(grader.key, Set.empty)
      val latestTime = candidates.map(_.timestamp).maxOption
      val latest = candidates.filter(event => latestTime.contains(event.timestamp))
      val completed = latest.collect { case event: GradingCompletedEvent[F] =>
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

  def mergeWith(other: InteractionVariableHistory[F]): InteractionVariableHistory[F] =
    copy(events = events.merge(other.events))

  def mergeWith(others: Seq[InteractionVariableHistory[F]]): InteractionVariableHistory[F] =
    others.foldLeft(this)(_.mergeWith(_))

  def cleanWithStrategy(syncStrategy: EventSyncStrategy): InteractionVariableHistory[F] =
    copy(events = InteractionEvents(syncStrategy.selectEvents(events.values)))

  /** Persistence cleanup only: no answer, grading or scaffolding events are synthesized. */
  def clean(): InteractionVariableHistory[F] =
    copy(events = InteractionEvents(events.values.filter(_.updateImportance != UpdateImportance.TEMPORARY)))
}

object InteractionVariableHistory {
  def empty[F <: InteractionFormat]: InteractionVariableHistory[F] = InteractionVariableHistory[F](InteractionEvents.empty)

  /** Build events from typed answers and completed results; this does not decode an older saved schema. */
  def fromResults[F <: InteractionFormat](states: Set[InteractionVariableState[F#InteractionStateType]],
      gradings: Set[GradedState[F#InteractionStateType, F#GradingInfoType]] = Set.empty[GradedState[F#InteractionStateType, F#GradingInfoType]], scaffoldings: Set[ScaffoldingResult[F#InteractionStateType, F#ScaffoldingInfoType]] = Set.empty[ScaffoldingResult[F#InteractionStateType, F#ScaffoldingInfoType]]): InteractionVariableHistory[F] =
    empty[F].addStates(states).addGradings(gradings.flatMap(_.graderResultMap.values)).addScaffoldings(scaffoldings)

  def fromResults[F <: InteractionFormat](states: InteractionVariableStateHistory[F#InteractionStateType], gradings: InteractionVariableGradingHistory[F#InteractionStateType, F#GradingInfoType],
      scaffoldings: InteractionVariableScaffoldingHistory[F#InteractionStateType, F#ScaffoldingInfoType]): InteractionVariableHistory[F] =
    empty[F].addStates(states.events).addGradings(gradings.gradingsFinished.flatMap(_.graderResultMap.values)).addScaffoldings(scaffoldings.scaffoldingsFinished)

  // no backward compatible version!
}
