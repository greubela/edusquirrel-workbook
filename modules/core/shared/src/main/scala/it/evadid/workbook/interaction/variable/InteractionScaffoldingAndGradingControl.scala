package it.evadid.workbook.interaction.variable

import it.evadid.core.datastructures.storage.AsyncDataCache
import it.evadid.core.datastructures.storage.CacheElementStatus.*
import it.evadid.distribution.command.SerializedException
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.workbook.abstractions.FeedbackEntity.SystemEntity
import it.evadid.workbook.abstractions.grading.GradingStatus.{NotFinished, NotRequested}
import it.evadid.workbook.abstractions.grading.{GradedState, WorkbookInteractionGrader}
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingResult
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingStatus.{ScaffoldingError, ScaffoldingRequestedButNotFinished}
import it.evadid.workbook.meta.ExecutionContextInformation

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

case class InteractionScaffoldingAndGradingControl[T, GA, SA](interactionVariable: InteractionVariable[T, GA, SA]) {

  def getCurrentStateOfGrading(forState: T): GradedState[T, GA] = interactionVariable.synchronized {
    val gradeMap = interactionVariable.currentHistory.historyGrading.gradingsMap
    val gradeInHistoryOp = interactionVariable.currentHistory.historyGrading.gradingsMap.get(forState)
    if (gradeInHistoryOp.isDefined) gradeInHistoryOp.get else {
      val gradeInCacheOp = gradingCache.getSyncIfInCache(forState, false)
      if (gradeInCacheOp.isDefined) gradeInCacheOp.get else {
        gradingCache.getCurrentElementStatus(forState).match {
          case UNKNOWN() => GradedState(forState).fillMapForUnavailable(interactionVariable.underlyingInteraction, NotRequested())
          case _ => GradedState(forState).fillMapForUnavailable(interactionVariable.underlyingInteraction, NotFinished())
        }
      }
    }
  }

  def getCurrentStateOfScaffolding(forState: T): Option[ScaffoldingResult[T, SA]] = interactionVariable.synchronized {
    val scaffMap = interactionVariable.currentHistory.historyScaffolding.scaffoldingMap
    val scaffInHistoryOp = interactionVariable.currentHistory.historyScaffolding.scaffoldingMap.get(forState)
    if (scaffInHistoryOp.isDefined) scaffInHistoryOp else {
      val scaffInCacheOp = scaffoldingCache.getSyncIfInCache(forState, false)
      if (scaffInCacheOp.isDefined) scaffInCacheOp else {
        scaffoldingCache.getCurrentElementStatus(forState).match {
          case UNKNOWN() => None
          case FAILED(err) => Some(ScaffoldingResult(forState, ScaffoldingError(err), SystemEntity(), None, LocalDateTime.now()))
          case anyStatus => {
            Some(ScaffoldingResult(forState, ScaffoldingRequestedButNotFinished(), SystemEntity(), None, LocalDateTime.now()))
          }
        }
      }
    }
  }

  // GRADING CALCULATION

  def getOrCalculateGrading(contextInformation: ExecutionContextInformation, forceGrading: Boolean = false): Future[GradedState[T, GA]] = interactionVariable.synchronized {
    val curValOp = interactionVariable.currentHistory.historyStates.lastStateOption
    if (curValOp.isEmpty) Future.failed(SerializedException("Cannot start grading on an InteractionVariable with no current state")) else {
      getOrCalculateGrading(contextInformation, forceGrading, curValOp.get.value)
    }
  }

  def getOrCalculateGrading(contextInformation: ExecutionContextInformation, forceGrading: Boolean, forState: T): Future[GradedState[T, GA]] = interactionVariable.synchronized {
    val ec = contextInformation.futureExecutionContext
    if (gradingCache.getCurrentElementStatus(forState) == STARTED()) {
      gradingCache.loadAsFuture(forState)(using ec).flatMap { completed =>
        if forceGrading && completed.testsWithResults.exists(_.gradingStatus == NotRequested()) then
          getOrCalculateGrading(contextInformation, true, forState)
        else Future.successful(completed)
      }(using ec)
    } else if (interactionVariable.currentHistory.historyGrading.gradingsMap.contains(forState)) {
      val saved = interactionVariable.currentHistory.historyGrading.gradingsMap(forState)
      if saved.state != forState then startGrading(contextInformation, forceGrading, forState)
      else retryGradingIfNecessary(contextInformation, forceGrading, saved)
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


  private def retryGradingIfNecessary(contextInformation: ExecutionContextInformation, forceGrading: Boolean, existing: GradedState[T, GA]): Future[GradedState[T, GA]] = interactionVariable.synchronized {
    val configured = interactionVariable.underlyingInteraction.gradingElements
    val valid = existing.copy(graderResultMap = existing.graderResultMap.filter { (grader, result) =>
      configured.contains(grader) && result.grader == grader && result.gradedState == existing.state
    })
    val missing = configured.filterNot(valid.graderResultMap.contains)
    val retry = if forceGrading then valid.shouldRetry else Seq.empty
    if (missing.isEmpty && retry.isEmpty) Future.successful(valid)
    else {
      val futureRes = valid.retryFailed(contextInformation, forceGrading)
        .zip(GradedState.executeGradingSafe(missing, existing.state, forceGrading, contextInformation))
        .map((retried, added) => added.merge(retried))(using contextInformation.futureExecutionContext)
      val published = futureRes.map { gradedState =>
        interactionVariable.updateHistory(_.addGrading(gradedState), contextInformation)
        gradedState
      }(using contextInformation.futureExecutionContext)
      gradingCache.informAboutExistingFuture(existing.state, published, true)
      gradingCache.loadAsFuture(existing.state)(using contextInformation.futureExecutionContext)
    }
  }

  private def startGrading(contextInformation: ExecutionContextInformation, forceGrading: Boolean, forState: T): Future[GradedState[T, GA]] = interactionVariable.synchronized {
    val graders: Seq[WorkbookInteractionGrader[T, GA]] = interactionVariable.underlyingInteraction.gradingElements
    val futureRes = GradedState.executeGradingSafe(graders, forState, forceGrading, contextInformation)
    val published = futureRes.map { gradedState =>
      interactionVariable.updateHistory(_.addGrading(gradedState), contextInformation)
      gradedState
    }(using contextInformation.futureExecutionContext)
    gradingCache.informAboutExistingFuture(forState, published, true)
    gradingCache.loadAsFuture(forState)(using contextInformation.futureExecutionContext)
  }

  // SCAFFOLDING CALCULATION

  def getOrCalculateScaffolding(contextInformation: ExecutionContextInformation): Future[ScaffoldingResult[T, SA]] = interactionVariable.synchronized {
    val curValOp = interactionVariable.currentHistory.historyStates.lastStateOption
    if (curValOp.isEmpty) Future.failed(SerializedException("Cannot start scaffolding on an InteractionVariable with no current state")) else {
      getOrCalculateScaffolding(contextInformation, curValOp.get.value)
    }
  }

  def getOrCalculateScaffolding(contextInformation: ExecutionContextInformation, forState: T): Future[ScaffoldingResult[T, SA]] = interactionVariable.synchronized {
    val ec = contextInformation.futureExecutionContext
    if (interactionVariable.currentHistory.historyScaffolding.scaffoldingMap.contains(forState)) {
      Future.successful(interactionVariable.currentHistory.historyScaffolding.scaffoldingMap(forState))
    } else if (scaffoldingCache.getCurrentElementStatus(forState) != UNKNOWN()) {
      scaffoldingCache.loadAsFuture(forState)(using ec)
    } else {
      startScaffolding(contextInformation, forState)
    }
  }

  private def startScaffolding(contextInformation: ExecutionContextInformation, forState: T): Future[ScaffoldingResult[T, SA]] = interactionVariable.synchronized {
    val ec = contextInformation.futureExecutionContext
    val scaffolderOp = interactionVariable.underlyingInteraction.scaffoldingElement

    if (scaffolderOp.isEmpty) Future.failed(SerializedException("Cannot calculate scaffolding on an interaction without scaffoldert!")) else {
      val scaffolder = scaffolderOp.get
      val futureRes = scaffolder.generateFeedbackSafe(forState, contextInformation)
      val published = futureRes.map { scaffResult =>
        interactionVariable.updateHistory(_.addScaffolding(scaffResult), contextInformation)
        scaffResult
      }(using ec)
      scaffoldingCache.informAboutExistingFuture(forState, published)
      scaffoldingCache.loadAsFuture(forState)(using ec)
    }
  }

  // caches

  private val logger = Logger.withNameAndPrefixes(None, PrintToStdLogger.printNothing)

  private val gradingCache: AsyncDataCache[T, GradedState[T, GA]] = new AsyncDataCache[T, GradedState[T, GA]](logger) {

    override protected def executeLoading(in: T)(ec: ExecutionContext): Future[GradedState[T, GA]] = {
      getOrCalculateGrading(ExecutionContextInformation.default, false, in)
    }

    override protected def formatInputForLogging(in: T): String = in.toString

    override protected def formatOutputForLogging(out: GradedState[T, GA]): String = out.toString
  }

  private val scaffoldingCache: AsyncDataCache[T, ScaffoldingResult[T, SA]] = new AsyncDataCache[T, ScaffoldingResult[T, SA]](logger) {

    override protected def executeLoading(in: T)(ec: ExecutionContext): Future[ScaffoldingResult[T, SA]] = {
      getOrCalculateScaffolding(ExecutionContextInformation.default, in)
    }

    override protected def formatInputForLogging(in: T): String = in.toString

    override protected def formatOutputForLogging(out: ScaffoldingResult[T, SA]): String = out.toString
  }

}
