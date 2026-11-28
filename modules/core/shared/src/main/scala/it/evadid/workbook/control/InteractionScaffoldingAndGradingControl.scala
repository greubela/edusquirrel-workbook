package it.evadid.workbook.control


import it.evadid.core.datastructures.state.State
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.abstractions.grading.{GradedState, GradingResult, GradingStatus, WorkbookInteractionGrader}
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingEvidence, ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.sync.UpdateImportance
import it.evadid.workbook.history.InteractionVariableHistory

import it.evadid.workbook.abstractions.ExecutionContextInformation

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future, Promise}

/** How feedback reads and records the history of its interaction in one session. */
trait FeedbackTarget[F <: InteractionFormat] {
  def interaction: WorkbookInteractionElement[F]
  def currentHistory: InteractionVariableHistory[F]
  def execution: ExecutionContextInformation
  /** False once the session has been replaced: requests then fail and results are dropped. */
  def isCurrent: Boolean
  def publish(transition: InteractionVariableHistory[F] => InteractionVariableHistory[F]): Unit
}

/** Grading and scaffolding of one interaction in one session.
  *
  * The history is the record of feedback: statuses are read from it, and every start and result is published into
  * it while it still holds the answer. This control adds what the history cannot hold: the requests in flight, which concurrent requests for the
  * same answer share, and the results computed in this session, which an answer that returns (for example after a
  * local reset) reuses and records again instead of computing them again.
  */
final class InteractionScaffoldingAndGradingControl[F <: InteractionFormat](target: FeedbackTarget[F]) {
  type InteractionStateType = InteractionFormat.State[F]
  type GradingInfoType = InteractionFormat.Grading[F]
  type ScaffoldingInfoType = InteractionFormat.Scaffolding[F]
  private type Graded = GradedState[InteractionStateType, GradingInfoType]
  private type Grader = WorkbookInteractionGrader[InteractionStateType, GradingInfoType]
  private type Hint = ScaffoldingResult[InteractionStateType, ScaffoldingInfoType]

  def interaction: WorkbookInteractionElement[F] = target.interaction
  private def history: InteractionVariableHistory[F] = target.currentHistory
  private def graders: Seq[Grader] = target.interaction.gradingElements

  private val pendingGradings = State(Map.empty[InteractionStateType, Future[Graded]])
  private val computedGradings = State(Map.empty[InteractionStateType, Graded])
  private val pendingHints = State(Map.empty[InteractionStateType, Future[Hint]])
  private val computedHints = State(Map.empty[InteractionStateType, Hint])

  // STATUS

  /** The recorded grading of `forState`: pending, completed or not requested per grader. */
  def getCurrentStateOfGrading(forState: InteractionStateType): Graded = history.gradingForState(graders, forState)

  /** The recorded hint of `forState`, pending or completed; `None` when none was requested. */
  def getCurrentStateOfScaffolding(forState: InteractionStateType): Option[Hint] = history.scaffoldingForState(forState)

  // GRADING

  def getOrCalculateGrading(forceGrading: Boolean = false): Future[Graded] = currentAnswer.fold(noAnswer)(getOrCalculateGrading(forceGrading, _))

  /** Joins a pending grading of `forState`. Otherwise grades the graders without a completed result that run for
    * this request, and, when forced, also retries those whose result asks for it; the other results are kept.
    */
  def getOrCalculateGrading(forceGrading: Boolean, forState: InteractionStateType): Future[Graded] =
    shared(pendingGradings, forState) { () =>
      val importance = importanceOf(forState)
      val completed = completedGrading(forState)
      val selected = graders.filter(grader => completed.graderResultMap.get(grader) match {
        case None => grader.shouldRunGrader(forceGrading, importance)
        case Some(result) => forceGrading && result.gradingStatus.shouldRetryExecution
      })
      val grading = if selected.isEmpty then Future.successful(completed) else {
        recordGradingStart(selected, forState)
        GradedState.executeGradingSafe(selected, forState, forceGrading, importance, target.execution)
          .map(_.merge(completed))(using target.execution.futureExecutionContext)
      }
      grading.map { graded =>
        computedGradings.update(_.updated(forState, graded))
        publishFor(forState)(_.addGrading(graded))
        graded.fillMapForUnavailable(graders, GradingStatus.NotRequested)
      }(using ExecutionContext.parasitic)
    }

  /** The completed results of `forState`: recorded in the history or, where it has none, computed in this session. */
  private def completedGrading(forState: InteractionStateType): Graded = {
    val recorded = history.gradingForState(graders, forState).graderResultMap
      .filter((_, result) => result.gradingStatus != GradingStatus.NotRequested && result.gradingStatus != GradingStatus.NotFinished)
    val computed = computedGradings.now().get(forState).fold(Map.empty)(_.graderResultMap)
      .collect { case (grader, result) if graders.exists(_.key == grader.key) => graders.find(_.key == grader.key).get -> result }
    GradedState(forState, computed ++ recorded)
  }

  private def recordGradingStart(selected: Seq[Grader], state: InteractionStateType): Unit = {
    val timestamp = Instant.now()
    target.publish(history => selected.foldLeft(history)(_.addGradingStart(state, _, timestamp)))
  }

  /** The importance the answer was recorded with (its latest event); an answer without an event has DEFAULT importance. */
  private def importanceOf(forState: InteractionStateType): UpdateImportance =
    history.allStates.filter(_.newState == forState).maxByOption(_.timestamp).map(_.updateImportance).getOrElse(UpdateImportance.DEFAULT)

  // SCAFFOLDING

  /** Joins a pending hint of the current answer, returns its completed hint (an error only unless `retryErrors`), or
    * asks the scaffolder.
    */
  def getOrCalculateScaffolding(retryErrors: Boolean = false): Future[Hint] =
    currentAnswer.fold(noAnswer)(forState => shared(pendingHints, forState) { () =>
      val completed = history.completedScaffolding(forState).orElse(computedHints.now().get(forState))
      val hint = completed.filterNot(result => retryErrors && result.scaffoldingStatus.isError).fold(requestHint(forState))(Future.successful)
      hint.map { result =>
        computedHints.update(_.updated(forState, result))
        publishFor(forState)(_.addScaffolding(result))
        result
      }(using ExecutionContext.parasitic)
    })

  private def requestHint(forState: InteractionStateType): Future[Hint] = target.interaction.scaffoldingElement match {
    case None => Future.failed(SerializedException("Cannot calculate scaffolding on an interaction without a scaffolder"))
    case Some(scaffolder) =>
      target.publish(_.addScaffolding(ScaffoldingResult(forState, ScaffoldingStatus.ScaffoldingRequestedButNotFinished(), scaffolder.scaffoldingEntity, None)))
      scaffolder.generateFeedbackSafe(forState, evidenceFor(forState), target.execution)
  }

  /** What a hint about `forState` reads from the history when the scaffolder asks for it: the latest answer and the
    * grader results recorded for `forState`.
    */
  def evidenceFor(forState: InteractionStateType): ScaffoldingEvidence.Reader[InteractionStateType, GradingInfoType] = () =>
    ScaffoldingEvidence(history.historyStates.lastStateOption, history.completedGradings(graders, forState).toList)

  // SHARED REQUESTS

  /** Records feedback of `forState` while the history still holds that answer; feedback of an answer that was
    * superseded and cleaned up meanwhile is dropped.
    */
  private def publishFor(forState: InteractionStateType)(transition: InteractionVariableHistory[F] => InteractionVariableHistory[F]): Unit =
    target.publish(history => if history.allStates.exists(_.newState == forState) then transition(history) else history)

  private def currentAnswer: Option[InteractionStateType] = history.historyStates.lastStateOption.map(_.value)
  private def noAnswer[A]: Future[A] = Future.failed(SerializedException("Cannot request feedback without a current answer"))

  /** Starts `request` for `forState` unless one is pending, which is shared instead. The request is registered before
    * it starts, so a request it causes (an automatic grading of the start events it records) joins it. A replaced
    * session starts nothing.
    */
  private def shared[A](pending: State[Map[InteractionStateType, Future[A]]], forState: InteractionStateType)(request: () => Future[A]): Future[A] =
    if !target.isCurrent then Future.failed(new java.util.concurrent.CancellationException("Feedback session ended"))
    else {
      val started = Promise[A]()
      val joined = pending.synchronized {
        val existing = pending.now().get(forState)
        if existing.isEmpty then pending.update(_.updated(forState, started.future))
        existing
      }
      joined.getOrElse {
        // The request leaves the pending ones before its result is published, so a continuation starts anew.
        scala.util.Try(request()).fold(Future.failed, identity).onComplete { outcome =>
          pending.update(all => if all.get(forState).contains(started.future) then all - forState else all)
          started.complete(outcome)
        }(using ExecutionContext.parasitic)
        started.future
      }
    }
}
