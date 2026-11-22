package it.evadid.workbook.interaction.variable

import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.abstractions.grading.{GradedState, GradingResult, GradingStatus, WorkbookInteractionGrader}
import it.evadid.workbook.interaction.sync.EventSyncStrategy
import it.evadid.workbook.interaction.variable.events.*

import java.time.LocalDateTime
import upickle.default.*

/** Canonical interaction history: all saved state and feedback live in timestamped events. */
case class InteractionVariableHistory[F <: InteractionFormat](
  events: InteractionEvents[F]
) {
  type Format = F
  type InteractionStateType = InteractionFormat.State[F]
  type GradingInfoType = InteractionFormat.Grading[F]
  type ScaffoldingInfoType = InteractionFormat.Scaffolding[F]
  lazy val allStates: Set[AnswerChangedEvent[F]] = events.values.collect {
    case event: AnswerChangedEvent[F] => event
  }
  lazy val gradingEvents: Set[GradingEvent[F]] = events.values.collect {
    case event: GradingEvent[F] => event
  }
  lazy val scaffoldingEvents: Set[ScaffoldingEvent[F]] = events.values.collect {
    case event: ScaffoldingEvent[F] => event
  }

  lazy val historyStates: InteractionVariableStateHistory[InteractionFormat.State[F]] = InteractionVariableStateHistory(
    allStates.map(event => InteractionVariableState(event.newState, event.updateImportance, event.timestamp)))
  lazy val historyGrading: InteractionVariableGradingHistory[InteractionFormat.State[F], InteractionFormat.Grading[F]] = InteractionVariableGradingHistory.fromResults(
    gradingEvents.collect { case event: GradingCompletedEvent[F] => event.result })
  lazy val historyScaffolding: InteractionVariableScaffoldingHistory[InteractionFormat.State[F], InteractionFormat.Scaffolding[F]] = InteractionVariableScaffoldingHistory(
    scaffoldingEvents.collect { case event: ScaffoldingCompletedEvent[F] => event.result })
  /** Confirmation requires the latest answer (including ties) and every feedback event for it. */
  def latestAnswerEvents: Set[InteractionEvent[F]] = {
    val candidates = cleanedHistory().allStates
    val latest = candidates.map(_.timestamp).maxOption
    val answers = candidates.filter(event => latest.contains(event.timestamp))
    val states = answers.map(_.associatedState)
    answers.map(event => event: InteractionEvent[F]) ++
      (gradingEvents ++ scaffoldingEvents).filter(event => states(event.associatedState))
  }

  def timestampLastChanged: Option[LocalDateTime] = events.timestampLastChanged
  def size: Int = events.values.size

  def addEvent(event: InteractionEvent[F]): InteractionVariableHistory[F] =
    copy(events = events.updated(event))
  def addState(state: InteractionVariableState[InteractionFormat.State[F]]): InteractionVariableHistory[F] =
    if allStates.exists(event => event.newState == state.value && event.timestamp == state.timestamp && event.updateImportance == state.updateImportance) then this
    else addEvent(AnswerChangedEvent(state.value, state.timestamp, state.updateImportance))
  def addStates(states: Iterable[InteractionVariableState[InteractionFormat.State[F]]]): InteractionVariableHistory[F] =
    states.foldLeft(this)(_.addState(_))
  def addGrading(result: GradingResult[InteractionFormat.State[F], InteractionFormat.Grading[F]]): InteractionVariableHistory[F] = result.gradingStatus match {
    case _: GradingStatus.NotRequested | _: GradingStatus.NotFinished => this
    case _ if gradingEvents.exists {
      case event: GradingCompletedEvent[F] => event.result == result
      case _ => false
    } => this
    case _ => addEvent(GradingCompletedEvent(result))
  }
  def addGrading(result: GradedState[InteractionFormat.State[F], InteractionFormat.Grading[F]]): InteractionVariableHistory[F] =
    result.graderResultMap.values.foldLeft(this)(_.addGrading(_))
  def addGradings(results: Iterable[GradingResult[InteractionFormat.State[F], InteractionFormat.Grading[F]]]): InteractionVariableHistory[F] =
    results.foldLeft(this)(_.addGrading(_))
  def addScaffolding(result: ScaffoldingResult[InteractionFormat.State[F], InteractionFormat.Scaffolding[F]]): InteractionVariableHistory[F] = result.scaffoldingStatus match {
    case _: ScaffoldingStatus.ScaffoldingRequestedButNotFinished if scaffoldingEvents.exists {
      case event: ScaffoldingStartedEvent[F] => event.associatedState == result.forState && event.timestamp == result.scaffoldingTime && event.scaffoldingEntity == result.scaffoldingEntity
      case _ => false
    } => this
    case _: ScaffoldingStatus.ScaffoldingRequestedButNotFinished =>
      addEvent(ScaffoldingStartedEvent(result.forState, result.scaffoldingTime, result.scaffoldingEntity))
    case _ if scaffoldingEvents.exists {
      case event: ScaffoldingCompletedEvent[F] => event.result == result
      case _ => false
    } => this
    case _ => addEvent(ScaffoldingCompletedEvent(result))
  }
  def addScaffoldings(results: Iterable[ScaffoldingResult[InteractionFormat.State[F], InteractionFormat.Scaffolding[F]]]): InteractionVariableHistory[F] =
    results.foldLeft(this)(_.addScaffolding(_))

  /** Removes superseded DEFAULT and TEMPORARY answers with the feedback of only those answers; see
    * [[InteractionVariableStateHistory.cleanedHistory]]. Feedback of retained answers stays.
    */
  def cleanedHistory(): InteractionVariableHistory[F] = {
    val states = historyStates.cleanedHistory().events
    val obsolete = allStates.filterNot(event => states(InteractionVariableState(event.newState, event.updateImportance, event.timestamp)))
    val obsoleteValues = obsolete.map(_.associatedState) -- states.map(_.value)
    copy(events = InteractionEvents(events.values.filterNot(event => obsoleteValues(event.associatedState)) -- obsolete))
  }

  /** Project the latest lifecycle event per configured grader identity. Completion wins a timestamp tie. */
  def gradingForState(knownGraders: Seq[WorkbookInteractionGrader[InteractionFormat.State[F], InteractionFormat.Grading[F]]], state: InteractionFormat.State[F]): GradedState[InteractionFormat.State[F], InteractionFormat.Grading[F]] = {
    val relevant = gradingEvents.filter(_.associatedState == state).groupBy(_.grader.key)
    val results = knownGraders.distinctBy(_.key).map { grader =>
      val candidates = relevant.getOrElse(grader.key, Set.empty)
      val latestTime = candidates.map(_.timestamp).maxOption
      val latest = candidates.filter(event => latestTime.contains(event.timestamp))
      val completed = candidates.collect { case event: GradingCompletedEvent[F] if latest.exists(_.isInstanceOf[GradingCompletedEvent[F]]) =>
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

  def scaffoldingForState(state: InteractionFormat.State[F]): Option[ScaffoldingResult[InteractionFormat.State[F], InteractionFormat.Scaffolding[F]]] = {
    val relevant = scaffoldingEvents.filter(_.associatedState == state)
    val latestTime = relevant.map(_.timestamp).maxOption
    val latest = relevant.filter(event => latestTime.contains(event.timestamp))
    if latest.exists(_.isInstanceOf[ScaffoldingCompletedEvent[F]]) then historyScaffolding.scaffoldingMap.get(state)
    else latest.collectFirst { case event: ScaffoldingStartedEvent[F] =>
      ScaffoldingResult(state, ScaffoldingStatus.ScaffoldingRequestedButNotFinished(), event.scaffoldingEntity, None, event.timestamp)
    }
  }

  def mergeWith(other: InteractionVariableHistory[F]): InteractionVariableHistory[F] =
    copy(events = events.merge(other.events))

  def mergeWith(others: Seq[InteractionVariableHistory[F]]): InteractionVariableHistory[F] =
    others.foldLeft(this)(_.mergeWith(_))

  def cleanWithStrategy(syncStrategy: EventSyncStrategy): InteractionVariableHistory[F] =
    copy(events = InteractionEvents(syncStrategy.selectEvents(events.values)))
}

object InteractionVariableHistory {
  given [F <: InteractionFormat](using format: ValueOf[F]): ReadWriter[InteractionVariableHistory[F]] =
    InteractionFormat.codecsFor(format.value).historyReadWriter

  def empty[F <: InteractionFormat]: InteractionVariableHistory[F] = InteractionVariableHistory[F](InteractionEvents.empty)

  /** Build events from typed answers and completed results; this does not decode an older saved schema. */
  def fromResults[F <: InteractionFormat](states: Set[InteractionVariableState[InteractionFormat.State[F]]],
      gradings: Set[GradedState[InteractionFormat.State[F], InteractionFormat.Grading[F]]] = Set.empty[GradedState[InteractionFormat.State[F], InteractionFormat.Grading[F]]], scaffoldings: Set[ScaffoldingResult[InteractionFormat.State[F], InteractionFormat.Scaffolding[F]]] = Set.empty[ScaffoldingResult[InteractionFormat.State[F], InteractionFormat.Scaffolding[F]]]): InteractionVariableHistory[F] =
    empty[F].addStates(states).addGradings(gradings.flatMap(_.graderResultMap.values)).addScaffoldings(scaffoldings)

  def fromResults[F <: InteractionFormat](states: InteractionVariableStateHistory[InteractionFormat.State[F]], gradings: InteractionVariableGradingHistory[InteractionFormat.State[F], InteractionFormat.Grading[F]],
      scaffoldings: InteractionVariableScaffoldingHistory[InteractionFormat.State[F], InteractionFormat.Scaffolding[F]]): InteractionVariableHistory[F] =
    empty[F].addStates(states.events).addGradings(gradings.gradingsFinished.flatMap(_.graderResultMap.values)).addScaffoldings(scaffoldings.scaffoldingsFinished)

  // no backward compatible version!
}
