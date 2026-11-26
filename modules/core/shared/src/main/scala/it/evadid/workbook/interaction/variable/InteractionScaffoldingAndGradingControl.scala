package it.evadid.workbook.interaction.variable

import it.evadid.workbook.interaction.sync.UpdateImportance
import it.evadid.core.datastructures.storage.AsyncDataCache
import it.evadid.core.datastructures.storage.CacheElementStatus.*
import it.evadid.distribution.command.SerializedException
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.workbook.abstractions.FeedbackEntity.SystemEntity
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.abstractions.grading.GradingStatus.{NotFinished, NotRequested}
import it.evadid.workbook.abstractions.grading.{GradedState, WorkbookInteractionGrader}
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingResult
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingStatus.{ScaffoldingError, ScaffoldingRequestedButNotFinished}
import it.evadid.workbook.interaction.variable.events.{GradingStartedEvent, ScaffoldingStartedEvent}
import it.evadid.workbook.meta.ExecutionContextInformation

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

final class InteractionScaffoldingAndGradingControl[F <: InteractionFormat](val interactionVariable: InteractionVariable[F]) {
  private val generation = interactionVariable.currentFeedbackGeneration

  // Type members for convenience
  type InteractionStateType = InteractionFormat.State[F]
  type GradingInfoType = InteractionFormat.Grading[F]
  type ScaffoldingInfoType = InteractionFormat.Scaffolding[F]

  def getCurrentStateOfGrading(forState: InteractionStateType): GradedState[InteractionStateType, GradingInfoType] = interactionVariable.synchronized {
    val history = interactionVariable.currentHistory
    val graders = interactionVariable.underlyingInteraction.gradingElements
    if history.gradingEvents.exists(_.associatedState == forState) then history.gradingForState(graders, forState)
    else gradingCache.getSyncIfInCache(forState, false).map(_.fillMapForUnavailable(graders, NotRequested))
      .getOrElse(history.gradingForState(graders, forState))
  }

  private def recordGradingStart(graders: Seq[WorkbookInteractionGrader[InteractionStateType, GradingInfoType]],
      state: InteractionStateType, context: ExecutionContextInformation): Unit = {
    val timestamp = LocalDateTime.now()
    interactionVariable.updateHistory(history => graders.foldLeft(history)((current, grader) =>
      current.addEvent(GradingStartedEvent[F](state, timestamp, grader))), context)
  }

  def getCurrentStateOfScaffolding(forState: InteractionStateType): Option[ScaffoldingResult[InteractionStateType, ScaffoldingInfoType]] = interactionVariable.synchronized {
    interactionVariable.currentHistory.scaffoldingForState(forState)
      .orElse(scaffoldingCache.getSyncIfInCache(forState, false))
      .orElse(scaffoldingCache.getCurrentElementStatus(forState) match {
        case UNKNOWN() => None
        case FAILED(error) => Some(ScaffoldingResult(forState, ScaffoldingError(error), SystemEntity, None, LocalDateTime.now()))
        case _ => Some(ScaffoldingResult(forState, ScaffoldingRequestedButNotFinished(), SystemEntity, None, LocalDateTime.now()))
      })
  }

  // GRADING CALCULATION
  def getOrCalculateGrading(contextInformation: ExecutionContextInformation, forceGrading: Boolean = false): Future[GradedState[InteractionStateType, GradingInfoType]] = interactionVariable.synchronized {
    val curValOp = interactionVariable.currentHistory.historyStates.lastStateOption
    if (curValOp.isEmpty) Future.failed(SerializedException("Cannot start grading on an InteractionVariable with no current state")) else {
      getOrCalculateGrading(contextInformation, forceGrading, curValOp.get.value)
    }
  }

  def getOrCalculateGrading(contextInformation: ExecutionContextInformation, forceGrading: Boolean, forState: InteractionStateType): Future[GradedState[InteractionStateType, GradingInfoType]] = interactionVariable.synchronized {
    val ec = contextInformation.futureExecutionContext
    if !contextInformation.isCurrent || generation != interactionVariable.currentFeedbackGeneration then
      Future.failed(new java.util.concurrent.CancellationException("Feedback session ended"))
    else if (gradingCache.getCurrentElementStatus(forState) == STARTED()) {
      gradingCache.loadAsFuture(forState)(using ec)
    } else if (interactionVariable.currentHistory.historyGrading.gradingsMap.contains(forState)) {
      val saved = interactionVariable.currentHistory.historyGrading.gradingsMap(forState)
      val storedKeys = saved.graderResultMap.keys.map(_.key).toSet
      if interactionVariable.underlyingInteraction.gradingElements.exists(grader => !storedKeys(grader.key) &&
        grader.shouldRunGrader(forceGrading, importanceOf(forState))) then
        startGrading(contextInformation, forceGrading, forState)
      else retryGradingIfNecessary(contextInformation, forceGrading,
        saved.fillMapForUnavailable(interactionVariable.underlyingInteraction.gradingElements, NotRequested))
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


  private def retryGradingIfNecessary(contextInformation: ExecutionContextInformation, forceGrading: Boolean, existing: GradedState[InteractionStateType, GradingInfoType]): Future[GradedState[InteractionStateType, GradingInfoType]] = interactionVariable.synchronized {
    if (!forceGrading || existing.shouldRetry.isEmpty) Future.successful(existing)
    else {
      recordGradingStart(existing.shouldRetry, existing.state, contextInformation)
      val futureRes = existing.retryFailed(contextInformation, forceGrading, importanceOf(existing.state))
      val published = futureRes.map { gradedState =>
        interactionVariable.publishFeedback(generation, contextInformation)(_.addGrading(gradedState))
        gradedState
      }(using contextInformation.futureExecutionContext)
      gradingCache.informAboutExistingFuture(existing.state, published, true)
      gradingCache.loadAsFuture(existing.state)(using contextInformation.futureExecutionContext)
    }
  }

  /** The importance the answer was recorded with (its latest event); an answer without an event has DEFAULT importance. */
  private def importanceOf(forState: InteractionStateType): UpdateImportance =
    interactionVariable.currentHistory.allStates.filter(_.newState == forState).maxByOption(_.timestamp)
      .map(_.updateImportance).getOrElse(UpdateImportance.DEFAULT)

  private def startGrading(contextInformation: ExecutionContextInformation, forceGrading: Boolean, forState: InteractionStateType): Future[GradedState[InteractionStateType, GradingInfoType]] = interactionVariable.synchronized {
    val graders: Seq[WorkbookInteractionGrader[InteractionStateType, GradingInfoType]] = interactionVariable.underlyingInteraction.gradingElements
    val activeKeys = graders.map(_.key).toSet
    val existing = interactionVariable.currentHistory.historyGrading.gradingsMap.get(forState)
      .map(_.graderResultMap.filter((grader, _) => activeKeys(grader.key))).getOrElse(Map.empty)
    val storedKeys = existing.keys.map(_.key).toSet
    val selected = graders.filterNot(grader => storedKeys(grader.key))
    val importance = importanceOf(forState)
    recordGradingStart(selected.filter(_.shouldRunGrader(forceGrading, importance)), forState, contextInformation)
    val futureRes = GradedState.executeGradingSafe(selected, forState, forceGrading, importance, contextInformation)
      .map(_.merge(GradedState(forState, existing)))(using contextInformation.futureExecutionContext)
    val published = futureRes.map { gradedState =>
      interactionVariable.publishFeedback(generation, contextInformation)(_.addGrading(gradedState))
      gradedState
    }(using contextInformation.futureExecutionContext)
    gradingCache.informAboutExistingFuture(forState, published, true)
    gradingCache.loadAsFuture(forState)(using contextInformation.futureExecutionContext)
  }

  // SCAFFOLDING CALCULATION

  def getOrCalculateScaffolding(contextInformation: ExecutionContextInformation): Future[ScaffoldingResult[InteractionStateType, ScaffoldingInfoType]] = interactionVariable.synchronized {
    val curValOp = interactionVariable.currentHistory.historyStates.lastStateOption
    if (curValOp.isEmpty) Future.failed(SerializedException("Cannot start scaffolding on an InteractionVariable with no current state")) else {
      getOrCalculateScaffolding(contextInformation, curValOp.get.value)
    }
  }

  def getOrCalculateScaffolding(contextInformation: ExecutionContextInformation, forState: InteractionStateType): Future[ScaffoldingResult[InteractionStateType, ScaffoldingInfoType]] = interactionVariable.synchronized {
    val ec = contextInformation.futureExecutionContext
    if !contextInformation.isCurrent || generation != interactionVariable.currentFeedbackGeneration then
      Future.failed(new java.util.concurrent.CancellationException("Feedback session ended"))
    else if (interactionVariable.currentHistory.historyScaffolding.scaffoldingMap.contains(forState)) {
      Future.successful(interactionVariable.currentHistory.historyScaffolding.scaffoldingMap(forState))
    } else if (scaffoldingCache.getCurrentElementStatus(forState) != UNKNOWN()) {
      scaffoldingCache.loadAsFuture(forState)(using ec)
    } else {
      startScaffolding(contextInformation, forState)
    }
  }

  private def startScaffolding(contextInformation: ExecutionContextInformation, forState: InteractionStateType): Future[ScaffoldingResult[InteractionStateType, ScaffoldingInfoType]] = interactionVariable.synchronized {
    val ec = contextInformation.futureExecutionContext
    val scaffolderOp = interactionVariable.underlyingInteraction.scaffoldingElement

    if (scaffolderOp.isEmpty) Future.failed(SerializedException("Cannot calculate scaffolding on an interaction without scaffoldert!")) else {
      val scaffolder = scaffolderOp.get
      interactionVariable.updateHistory(_.addEvent(ScaffoldingStartedEvent[F](forState, LocalDateTime.now(), scaffolder.scaffoldingEntity)), contextInformation)
      val futureRes = scaffolder.generateFeedbackSafe(forState, contextInformation)
      val published = futureRes.map { scaffResult =>
        interactionVariable.publishFeedback(generation, contextInformation)(_.addScaffolding(scaffResult))
        scaffResult
      }(using ec)
      scaffoldingCache.informAboutExistingFuture(forState, published)
      scaffoldingCache.loadAsFuture(forState)(using ec)
    }
  }

  // caches

  private val logger = Logger.withNameAndPrefixes(None, PrintToStdLogger.printNothing)

  private val gradingCache: AsyncDataCache[InteractionStateType, GradedState[InteractionStateType, GradingInfoType]] = new AsyncDataCache[InteractionStateType, GradedState[InteractionStateType, GradingInfoType]](logger) {

    override protected def executeLoading(in: InteractionStateType)(ec: ExecutionContext): Future[GradedState[InteractionStateType, GradingInfoType]] = {
      getOrCalculateGrading(ExecutionContextInformation.default, false, in)
    }

    override protected def formatInputForLogging(in: InteractionStateType): String = in.toString

    override protected def formatOutputForLogging(out: GradedState[InteractionStateType, GradingInfoType]): String = out.toString
  }

  private val scaffoldingCache: AsyncDataCache[InteractionStateType, ScaffoldingResult[InteractionStateType, ScaffoldingInfoType]] = new AsyncDataCache[InteractionStateType, ScaffoldingResult[InteractionStateType, ScaffoldingInfoType]](logger) {
    override protected def executeLoading(in: InteractionStateType)(ec: ExecutionContext): Future[ScaffoldingResult[InteractionStateType, ScaffoldingInfoType]] = {
      getOrCalculateScaffolding(ExecutionContextInformation.default, in)
    }
    override protected def formatInputForLogging(in: InteractionStateType): String = in.toString
    override protected def formatOutputForLogging(out: ScaffoldingResult[InteractionStateType, ScaffoldingInfoType]): String = out.toString
  }

}
