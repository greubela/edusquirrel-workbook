package it.evadid.workbook.interaction.variable

import it.evadid.workbook.interaction.sync.UpdateImportance
import it.evadid.core.datastructures.storage.AsyncDataCache
import it.evadid.core.datastructures.storage.CacheElementStatus.*
import it.evadid.distribution.command.SerializedException
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.workbook.abstractions.FeedbackEntity.SystemEntity
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.abstractions.grading.GradingStatus.{NotFinished, NotRequested}
import it.evadid.workbook.abstractions.grading.{GradedState, WorkbookInteractionGrader}
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingEvidence, ScaffoldingResult}
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingStatus.{ScaffoldingError, ScaffoldingRequestedButNotFinished}
import it.evadid.workbook.interaction.variable.events.{GradingStartedEvent, ScaffoldingStartedEvent}
import it.evadid.workbook.meta.ExecutionContextInformation

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

/** How feedback reads and records the history of its interaction in one session. */
trait FeedbackTarget[F <: InteractionFormat] {
  def interaction: WorkbookInteractionElement[F]
  def currentHistory: InteractionVariableHistory[F]
  def execution: ExecutionContextInformation
  /** False once the session has been replaced: requests then fail and results are dropped. */
  def isCurrent: Boolean
  def publish(transition: InteractionVariableHistory[F] => InteractionVariableHistory[F]): Unit
}

/** Grading and scaffolding of one interaction in one session. Feedback runs with the session's execution context
  * and is recorded in its history; once the session is replaced, requests fail and late results are dropped.
  */
final class InteractionScaffoldingAndGradingControl[F <: InteractionFormat](target: FeedbackTarget[F]) {
  private def history: InteractionVariableHistory[F] = target.currentHistory
  // Type members for convenience
  type InteractionStateType = InteractionFormat.State[F]
  type GradingInfoType = InteractionFormat.Grading[F]
  type ScaffoldingInfoType = InteractionFormat.Scaffolding[F]

  def getCurrentStateOfGrading(forState: InteractionStateType): GradedState[InteractionStateType, GradingInfoType] = this.synchronized {
    val history = target.currentHistory
    val graders = target.interaction.gradingElements
    if history.gradingEvents.exists(_.associatedState == forState) then history.gradingForState(graders, forState)
    else gradingCache.getSyncIfInCache(forState, false).map(_.fillMapForUnavailable(graders, NotRequested))
      .getOrElse(history.gradingForState(graders, forState))
  }

  private def recordGradingStart(graders: Seq[WorkbookInteractionGrader[InteractionStateType, GradingInfoType]],
      state: InteractionStateType): Unit = {
    val timestamp = LocalDateTime.now()
    target.publish(history => graders.foldLeft(history)((current, grader) =>
      current.addEvent(GradingStartedEvent[F](state, timestamp, grader))))
  }

  def getCurrentStateOfScaffolding(forState: InteractionStateType): Option[ScaffoldingResult[InteractionStateType, ScaffoldingInfoType]] = this.synchronized {
    history.scaffoldingForState(forState)
      .orElse(scaffoldingCache.getSyncIfInCache(forState, false))
      .orElse(scaffoldingCache.getCurrentElementStatus(forState) match {
        case UNKNOWN() => None
        case FAILED(error) => Some(ScaffoldingResult(forState, ScaffoldingError(error), SystemEntity, None, LocalDateTime.now()))
        case _ => Some(ScaffoldingResult(forState, ScaffoldingRequestedButNotFinished(), SystemEntity, None, LocalDateTime.now()))
      })
  }

  // GRADING CALCULATION
  def getOrCalculateGrading(forceGrading: Boolean = false): Future[GradedState[InteractionStateType, GradingInfoType]] = this.synchronized {
    history.historyStates.lastStateOption match {
      case Some(current) => getOrCalculateGrading(forceGrading, current.value)
      case None => Future.failed(SerializedException("Cannot start grading without a current answer"))
    }
  }

  def getOrCalculateGrading(forceGrading: Boolean, forState: InteractionStateType): Future[GradedState[InteractionStateType, GradingInfoType]] = this.synchronized {
    val contextInformation = target.execution
    val ec = contextInformation.futureExecutionContext
    if !target.isCurrent then sessionEndedFailure
    else if (gradingCache.getCurrentElementStatus(forState) == STARTED()) {
      gradingCache.loadAsFuture(forState)(using ec)
    } else if (history.historyGrading.gradingsMap.contains(forState)) {
      val saved = history.historyGrading.gradingsMap(forState)
      val storedKeys = saved.graderResultMap.keys.map(_.key).toSet
      if target.interaction.gradingElements.exists(grader => !storedKeys(grader.key) &&
        grader.shouldRunGrader(forceGrading, importanceOf(forState))) then
        startGrading(contextInformation, forceGrading, forState)
      else retryGradingIfNecessary(contextInformation, forceGrading,
        saved.fillMapForUnavailable(target.interaction.gradingElements, NotRequested))
    } else {
      gradingCache.getSyncIfInCache(forState, false).match {
        case Some(gradedState) =>
          retryGradingIfNecessary(contextInformation, forceGrading, gradedState)
        case None => gradingCache.getCurrentElementStatus(forState).match {
          case STARTED() => gradingCache.loadAsFuture(forState)(using ec)
          case _ => startGrading(contextInformation, forceGrading, forState)
        }
      }
    }
  }

  private def sessionEndedFailure[A]: Future[A] = Future.failed(new java.util.concurrent.CancellationException("Feedback session ended"))

  private def retryGradingIfNecessary(contextInformation: ExecutionContextInformation, forceGrading: Boolean, existing: GradedState[InteractionStateType, GradingInfoType]): Future[GradedState[InteractionStateType, GradingInfoType]] = this.synchronized {
    if (!forceGrading || existing.shouldRetry.isEmpty) Future.successful(existing)
    else {
      recordGradingStart(existing.shouldRetry, existing.state)
      publishGrading(existing.state, existing.retryFailed(contextInformation, forceGrading, importanceOf(existing.state)), contextInformation)
    }
  }

  /** Records the grading in the history once it completes and shares the pending result through the cache. */
  private def publishGrading(forState: InteractionStateType, grading: Future[GradedState[InteractionStateType, GradingInfoType]],
      contextInformation: ExecutionContextInformation): Future[GradedState[InteractionStateType, GradingInfoType]] = {
    val ec = contextInformation.futureExecutionContext
    val published = grading.map { gradedState =>
      target.publish(_.addGrading(gradedState))
      gradedState
    }(using ec)
    gradingCache.informAboutExistingFuture(forState, published, true)
    gradingCache.loadAsFuture(forState)(using ec)
  }

  /** The importance the answer was recorded with (its latest event); an answer without an event has DEFAULT importance. */
  private def importanceOf(forState: InteractionStateType): UpdateImportance =
    history.allStates.filter(_.newState == forState).maxByOption(_.timestamp)
      .map(_.updateImportance).getOrElse(UpdateImportance.DEFAULT)

  private def startGrading(contextInformation: ExecutionContextInformation, forceGrading: Boolean, forState: InteractionStateType): Future[GradedState[InteractionStateType, GradingInfoType]] = this.synchronized {
    val graders: Seq[WorkbookInteractionGrader[InteractionStateType, GradingInfoType]] = target.interaction.gradingElements
    val activeKeys = graders.map(_.key).toSet
    val existing = history.historyGrading.gradingsMap.get(forState)
      .map(_.graderResultMap.filter((grader, _) => activeKeys(grader.key))).getOrElse(Map.empty)
    val storedKeys = existing.keys.map(_.key).toSet
    val selected = graders.filterNot(grader => storedKeys(grader.key))
    val importance = importanceOf(forState)
    recordGradingStart(selected.filter(_.shouldRunGrader(forceGrading, importance)), forState)
    val grading = GradedState.executeGradingSafe(selected, forState, forceGrading, importance, contextInformation)
      .map(_.merge(GradedState(forState, existing)))(using contextInformation.futureExecutionContext)
    publishGrading(forState, grading, contextInformation)
  }

  // SCAFFOLDING CALCULATION

  def getOrCalculateScaffolding(retryErrors: Boolean = false): Future[ScaffoldingResult[InteractionStateType, ScaffoldingInfoType]] = this.synchronized {
    history.historyStates.lastStateOption match {
      case Some(current) => calculateScaffolding(target.execution, current.value, retryErrors)
      case None => Future.failed(SerializedException("Cannot start scaffolding without a current answer"))
    }
  }

  private def calculateScaffolding(contextInformation: ExecutionContextInformation, forState: InteractionStateType, retryErrors: Boolean): Future[ScaffoldingResult[InteractionStateType, ScaffoldingInfoType]] = this.synchronized {
    val ec = contextInformation.futureExecutionContext
    val cached = scaffoldingCache.getCurrentElementStatus(forState)
    if !target.isCurrent then sessionEndedFailure
    else if cached == STARTED() then scaffoldingCache.loadAsFuture(forState)(using ec)
    else if retryErrors && getCurrentStateOfScaffolding(forState).exists(_.scaffoldingStatus.isError) then startScaffolding(contextInformation, forState)
    else if (history.historyScaffolding.scaffoldingMap.contains(forState)) {
      Future.successful(history.historyScaffolding.scaffoldingMap(forState))
    } else if (cached != UNKNOWN()) {
      scaffoldingCache.loadAsFuture(forState)(using ec)
    } else {
      startScaffolding(contextInformation, forState)
    }
  }

  private def startScaffolding(contextInformation: ExecutionContextInformation, forState: InteractionStateType): Future[ScaffoldingResult[InteractionStateType, ScaffoldingInfoType]] = this.synchronized {
    val ec = contextInformation.futureExecutionContext
    target.interaction.scaffoldingElement match {
      case None => Future.failed(SerializedException("Cannot calculate scaffolding on an interaction without scaffoldert!"))
      case Some(scaffolder) =>
        target.publish(_.addEvent(ScaffoldingStartedEvent[F](forState, LocalDateTime.now(), scaffolder.scaffoldingEntity)))
        val evidence: ScaffoldingEvidence.Reader[InteractionStateType, GradingInfoType] = () => ScaffoldingEvidence(history.historyStates.lastStateOption,
          history.historyGrading.gradingsMap.get(forState).filter(_.state == forState).toList.flatMap(_.graderResultMap.values))
        val published = scaffolder.generateFeedbackSafe(forState, evidence, contextInformation).map { scaffResult =>
          target.publish(_.addScaffolding(scaffResult))
          scaffResult
        }(using ec)
        scaffoldingCache.informAboutExistingFuture(forState, published, replaceIfExist = true)
        scaffoldingCache.loadAsFuture(forState)(using ec)
    }
  }

  // caches

  private val logger = Logger.withNameAndPrefixes(None, PrintToStdLogger.printNothing)

  private val gradingCache: AsyncDataCache[InteractionStateType, GradedState[InteractionStateType, GradingInfoType]] = new AsyncDataCache[InteractionStateType, GradedState[InteractionStateType, GradingInfoType]](logger) {

    override protected def executeLoading(in: InteractionStateType)(ec: ExecutionContext): Future[GradedState[InteractionStateType, GradingInfoType]] = {
      getOrCalculateGrading(false, in)
    }

    override protected def formatInputForLogging(in: InteractionStateType): String = in.toString

    override protected def formatOutputForLogging(out: GradedState[InteractionStateType, GradingInfoType]): String = out.toString
  }

  private val scaffoldingCache: AsyncDataCache[InteractionStateType, ScaffoldingResult[InteractionStateType, ScaffoldingInfoType]] = new AsyncDataCache[InteractionStateType, ScaffoldingResult[InteractionStateType, ScaffoldingInfoType]](logger) {
    override protected def executeLoading(in: InteractionStateType)(ec: ExecutionContext): Future[ScaffoldingResult[InteractionStateType, ScaffoldingInfoType]] = {
      calculateScaffolding(target.execution, in, retryErrors = false)
    }
    override protected def formatInputForLogging(in: InteractionStateType): String = in.toString
    override protected def formatOutputForLogging(out: ScaffoldingResult[InteractionStateType, ScaffoldingInfoType]): String = out.toString
  }

}
