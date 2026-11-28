package it.evadid.workbook.history

import it.evadid.workbook.abstractions.{GraderReference, InteractionFormat, StateReference}
import it.evadid.workbook.abstractions.grading.GradingRecord
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingRecord
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.abstractions.grading.{GradedState, GradingResult, GradingStatus, WorkbookInteractionGrader}
import it.evadid.workbook.sync.EventSyncStrategy
import it.evadid.workbook.history.events.*

import java.time.Instant
import upickle.default.*

/** Canonical interaction history: all saved state and feedback live in timestamped events. The format derives the
  * references feedback events use for answers.
  */
case class InteractionVariableHistory[F <: InteractionFormat](
  format: F,
  events: InteractionEvents[F]
) {
  type Format = F
  type InteractionStateType = InteractionFormat.State[F]
  type GradingInfoType = InteractionFormat.Grading[F]
  type ScaffoldingInfoType = InteractionFormat.Scaffolding[F]
  /** The reference feedback uses for `state`. */
  def referenceOf(state: InteractionFormat.State[F]): StateReference = InteractionFormat.referenceOf(format, state)

  private lazy val answerReferences: Map[AnswerChangedEvent[F], StateReference] =
    allStates.map(event => event -> referenceOf(event.newState)).toMap

  /** The answer an event belongs to: an answer event's own, or the one a feedback event refers to. */
  def stateOf(event: InteractionEvent[F]): StateReference = event match {
    case answer: AnswerChangedEvent[F] => answerReferences.getOrElse(answer, referenceOf(answer.newState))
    case feedback: FeedbackEvent[F] => feedback.state
  }

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
  /** Completed gradings per answer and grader, merged; readers resolve them with [[gradingForState]]. */
  lazy val gradingRecords: Map[StateReference, Map[GraderReference, GradingRecord[InteractionFormat.Grading[F]]]] =
    gradingEvents.collect { case event: GradingCompletedEvent[F] => event.record }.groupBy(_.state).view
      .mapValues(_.groupBy(_.grader).view.mapValues(_.reduce(_.merge(_))).toMap).toMap
  /** Completed hints per answer, merged; readers resolve them with [[scaffoldingForState]]. */
  lazy val scaffoldingRecords: Map[StateReference, ScaffoldingRecord[InteractionFormat.Scaffolding[F]]] =
    scaffoldingEvents.collect { case event: ScaffoldingCompletedEvent[F] => event.record }.groupBy(_.state).view
      .mapValues(_.reduce(_.merge(_))).toMap
  /** Confirmation requires the latest answer (including ties) and every feedback event for it. */
  def latestAnswerEvents: Set[InteractionEvent[F]] = {
    val answers = InteractionEvents.latestTies(cleanedHistory().allStates)
    val states = answers.map(stateOf)
    answers.map(event => event: InteractionEvent[F]) ++
      (gradingEvents ++ scaffoldingEvents).filter(event => states(event.state))
  }

  def timestampLastChanged: Option[Instant] = events.timestampLastChanged
  def size: Int = events.values.size

  def addEvent(event: InteractionEvent[F]): InteractionVariableHistory[F] =
    copy(events = events.updated(event))
  def addState(state: InteractionVariableState[InteractionFormat.State[F]]): InteractionVariableHistory[F] =
    if allStates.exists(event => event.newState == state.value && event.timestamp == state.timestamp && event.updateImportance == state.updateImportance) then this
    else addEvent(AnswerChangedEvent(state.value, state.timestamp, state.updateImportance))
  def addStates(states: Iterable[InteractionVariableState[InteractionFormat.State[F]]]): InteractionVariableHistory[F] =
    states.foldLeft(this)(_.addState(_))
  def addGrading(result: GradingResult[InteractionFormat.State[F], InteractionFormat.Grading[F]]): InteractionVariableHistory[F] = result.gradingStatus match {
    case GradingStatus.NotRequested | GradingStatus.NotFinished => this
    case _ =>
      val record = result.record(referenceOf(result.gradedState))
      if gradingEvents.exists {
        case event: GradingCompletedEvent[F] => event.record == record
        case _ => false
      } then this
      else addEvent(GradingCompletedEvent(record))
  }
  /** Records that `grader` started grading `state`. */
  def addGradingStart(state: InteractionFormat.State[F],
      grader: WorkbookInteractionGrader[InteractionFormat.State[F], InteractionFormat.Grading[F]], timestamp: Instant): InteractionVariableHistory[F] =
    addEvent(GradingStartedEvent(referenceOf(state), timestamp, grader.reference))
  def addGrading(result: GradedState[InteractionFormat.State[F], InteractionFormat.Grading[F]]): InteractionVariableHistory[F] =
    result.graderResultMap.values.foldLeft(this)(_.addGrading(_))
  def addGradings(results: Iterable[GradingResult[InteractionFormat.State[F], InteractionFormat.Grading[F]]]): InteractionVariableHistory[F] =
    results.foldLeft(this)(_.addGrading(_))
  def addScaffolding(result: ScaffoldingResult[InteractionFormat.State[F], InteractionFormat.Scaffolding[F]]): InteractionVariableHistory[F] = {
    val record = result.record(referenceOf(result.forState))
    result.scaffoldingStatus match {
      case _: ScaffoldingStatus.ScaffoldingRequestedButNotFinished if scaffoldingEvents.exists {
        case event: ScaffoldingStartedEvent[F] => event.state == record.state && event.timestamp == record.scaffoldingTime && event.scaffoldingEntity == record.scaffoldingEntity
        case _ => false
      } => this
      case _: ScaffoldingStatus.ScaffoldingRequestedButNotFinished =>
        addEvent(ScaffoldingStartedEvent(record.state, record.scaffoldingTime, record.scaffoldingEntity))
      case _ if scaffoldingEvents.exists {
        case event: ScaffoldingCompletedEvent[F] => event.record == record
        case _ => false
      } => this
      case _ => addEvent(ScaffoldingCompletedEvent(record))
    }
  }
  def addScaffoldings(results: Iterable[ScaffoldingResult[InteractionFormat.State[F], InteractionFormat.Scaffolding[F]]]): InteractionVariableHistory[F] =
    results.foldLeft(this)(_.addScaffolding(_))

  /** Removes superseded DEFAULT and TEMPORARY answers with the feedback of only those answers; see
    * [[InteractionVariableStateHistory.cleanedHistory]]. Feedback of retained answers stays.
    */
  def cleanedHistory(): InteractionVariableHistory[F] = {
    val states = historyStates.cleanedHistory().events
    val obsolete = allStates.filterNot(event => states(InteractionVariableState(event.newState, event.updateImportance, event.timestamp)))
    val obsoleteAnswers = obsolete.map(stateOf) -- states.map(state => referenceOf(state.value))
    copy(events = InteractionEvents(events.values.filterNot(event => obsoleteAnswers(stateOf(event))) -- obsolete))
  }

  /** Project the latest lifecycle event per configured grader identity. Completion wins a timestamp tie. */
  def gradingForState(knownGraders: Seq[WorkbookInteractionGrader[InteractionFormat.State[F], InteractionFormat.Grading[F]]], state: InteractionFormat.State[F]): GradedState[InteractionFormat.State[F], InteractionFormat.Grading[F]] = {
    val answer = referenceOf(state)
    val relevant = gradingEvents.filter(_.state == answer).groupBy(_.grader)
    val results = knownGraders.distinctBy(_.key).map { grader =>
      val candidates = relevant.getOrElse(grader.reference, Set.empty)
      val latest = InteractionEvents.latestTies(candidates)
      val latestTime = latest.headOption.map(_.timestamp)
      val completed = candidates.collect { case event: GradingCompletedEvent[F] if latest.exists(_.isInstanceOf[GradingCompletedEvent[F]]) =>
        event.record.resolve(grader, state)
      }
      val result = completed.reduceOption(_.merge(_)).getOrElse(GradingResult(
        grader, state,
        if latest.nonEmpty then GradingStatus.NotFinished else GradingStatus.NotRequested,
        None, None, latestTime.getOrElse(Instant.MIN)))
      grader -> result
    }.toMap
    GradedState(state, results)
  }

  def scaffoldingForState(state: InteractionFormat.State[F]): Option[ScaffoldingResult[InteractionFormat.State[F], InteractionFormat.Scaffolding[F]]] = {
    val answer = referenceOf(state)
    val relevant = scaffoldingEvents.filter(_.state == answer)
    val latest = InteractionEvents.latestTies(relevant)
    if latest.exists(_.isInstanceOf[ScaffoldingCompletedEvent[F]]) then completedScaffolding(state)
    else latest.collectFirst { case event: ScaffoldingStartedEvent[F] =>
      ScaffoldingResult(state, ScaffoldingStatus.ScaffoldingRequestedButNotFinished(), event.scaffoldingEntity, None, event.timestamp)
    }
  }

  /** The completed results for `state` of those `graders` that recorded one. */
  def completedGradings(graders: Seq[WorkbookInteractionGrader[InteractionFormat.State[F], InteractionFormat.Grading[F]]],
      state: InteractionFormat.State[F]): Seq[GradingResult[InteractionFormat.State[F], InteractionFormat.Grading[F]]] = {
    val recorded = gradingRecords.getOrElse(referenceOf(state), Map.empty)
    graders.distinctBy(_.key).flatMap(grader => recorded.get(grader.reference).map(_.resolve(grader, state)))
  }

  /** The completed hint for `state`, if one was recorded. */
  def completedScaffolding(state: InteractionFormat.State[F]): Option[ScaffoldingResult[InteractionFormat.State[F], InteractionFormat.Scaffolding[F]]] =
    scaffoldingRecords.get(referenceOf(state)).map(_.resolve(state))

  def mergeWith(other: InteractionVariableHistory[F]): InteractionVariableHistory[F] =
    copy(events = events.merge(other.events))

  def mergeWith(others: Seq[InteractionVariableHistory[F]]): InteractionVariableHistory[F] =
    others.foldLeft(this)(_.mergeWith(_))

  def cleanWithStrategy(syncStrategy: EventSyncStrategy): InteractionVariableHistory[F] =
    copy(events = InteractionEvents(syncStrategy.selectEvents(events.values, stateOf)))
}

object InteractionVariableHistory {
  given [F <: InteractionFormat](using format: ValueOf[F]): ReadWriter[InteractionVariableHistory[F]] =
    InteractionFormat.codecsFor(format.value).historyReadWriter

  def empty[F <: InteractionFormat](format: F): InteractionVariableHistory[F] = InteractionVariableHistory[F](format, InteractionEvents.empty)

  /** Build events from typed answers and completed results; this does not decode an older saved schema. */
  def fromResults[F <: InteractionFormat](format: F, states: Set[InteractionVariableState[InteractionFormat.State[F]]],
      gradings: Set[GradedState[InteractionFormat.State[F], InteractionFormat.Grading[F]]] = Set.empty[GradedState[InteractionFormat.State[F], InteractionFormat.Grading[F]]], scaffoldings: Set[ScaffoldingResult[InteractionFormat.State[F], InteractionFormat.Scaffolding[F]]] = Set.empty[ScaffoldingResult[InteractionFormat.State[F], InteractionFormat.Scaffolding[F]]]): InteractionVariableHistory[F] =
    empty(format).addStates(states).addGradings(gradings.flatMap(_.graderResultMap.values)).addScaffoldings(scaffoldings)

  // no backward compatible version!
}
