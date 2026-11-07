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
import scala.util.Success

case class InteractionScaffoldingAndGradingControl[T, GA, SA](interactionVariable: InteractionVariable[T, GA, SA]) {

  def getCurrentStateOfGrading(): Option[GradedState[T, GA]] = interactionVariable.synchronized {
    val gradeMap = interactionVariable.currentHistory.historyGrading.gradingsMap
    val curValOp = interactionVariable.currentHistory.historyStates.lastStateOption
    if (curValOp.isEmpty) None else {
      val curVal = curValOp.get.value
      val gradeInHistoryOp = interactionVariable.currentHistory.historyGrading.gradingsMap.get(curVal)
      if (gradeInHistoryOp.isDefined) gradeInHistoryOp else {
        val gradeInCacheOp = gradingCache.getSyncIfInCache(curVal)
        if (gradeInCacheOp.isDefined) gradeInCacheOp else {
          gradingCache.getCurrentElementStatus(curVal).match {
            case UNKNOWN() => Some(GradedState(curVal).fillMapForUnavailable(interactionVariable.underlyingInteraction, NotRequested()))
            case _ => Some(GradedState(curVal).fillMapForUnavailable(interactionVariable.underlyingInteraction, NotFinished()))
          }
        }
      }
    }
  }

  def getCurrentStateOfScaffolding(): Option[ScaffoldingResult[T, SA]] = interactionVariable.synchronized {
    val scaffMap = interactionVariable.currentHistory.historyScaffolding.scaffoldingMap
    val curValOp = interactionVariable.currentHistory.historyStates.lastStateOption
    if (curValOp.isEmpty) None else {
      val curVal = curValOp.get.value
      val scaffInHistoryOp = interactionVariable.currentHistory.historyScaffolding.scaffoldingMap.get(curVal)
      if (scaffInHistoryOp.isDefined) scaffInHistoryOp else {
        val scaffInCacheOp = scaffoldingCache.getSyncIfInCache(curVal)
        if (scaffInCacheOp.isDefined) scaffInCacheOp else {
          scaffoldingCache.getCurrentElementStatus(curVal).match {
            case UNKNOWN() => None
            case FAILED(err) => Some(ScaffoldingResult(curVal, ScaffoldingError(err), SystemEntity(), None, LocalDateTime.now()))
            case _ => Some(ScaffoldingResult(curVal, ScaffoldingRequestedButNotFinished(), SystemEntity(), None, LocalDateTime.now()))
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
    if (interactionVariable.currentHistory.historyGrading.gradingsMap.contains(forState)) {
      Future.successful(interactionVariable.currentHistory.historyGrading.gradingsMap(forState))
    } else {
      gradingCache.getSyncIfInCache(forState, false).match {
        case Some(gradedState) =>
          retryGradingIfNecessary(contextInformation, forceGrading, gradedState)
        case None => gradingCache.getCurrentElementStatus(forState).match {
          case STARTED() => gradingCache.loadAsFuture(forState)(using contextInformation.futureExecutionContext)
          case _ => startGrading(contextInformation, forceGrading, forState)
        }
      }
    }
  }


  private def retryGradingIfNecessary(contextInformation: ExecutionContextInformation, forceGrading: Boolean, existing: GradedState[T, GA]): Future[GradedState[T, GA]] = interactionVariable.synchronized {
    if (!forceGrading || existing.shouldRetry.isEmpty) Future.successful(existing)
    else {
      val futureRes = existing.retryFailed(contextInformation, forceGrading)
      futureRes.onComplete {
        case Success(gradedState) => interactionVariable.updateHistory(_.addGrading(gradedState), contextInformation)
      }(using contextInformation.futureExecutionContext)
      gradingCache.informAboutExistingFuture(existing.state, futureRes, true)
      futureRes
    }
  }

  private def startGrading(contextInformation: ExecutionContextInformation, forceGrading: Boolean, forState: T): Future[GradedState[T, GA]] = interactionVariable.synchronized {
    val graders: Seq[WorkbookInteractionGrader[T, GA]] = interactionVariable.underlyingInteraction.gradingElements
    val futureRes = GradedState.executeGradingSafe(graders, forState, forceGrading, contextInformation)
    futureRes.onComplete {
      case Success(gradedState) => interactionVariable.updateHistory(_.addGrading(gradedState), contextInformation)
    }(using contextInformation.futureExecutionContext)
    gradingCache.informAboutExistingFuture(forState, futureRes, true)
    futureRes
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
      futureRes.onComplete {
        case Success(scaffResult) => interactionVariable.updateHistory(_.addScaffolding(scaffResult), contextInformation)
      }(using ec)
      scaffoldingCache.informAboutExistingFuture(forState, futureRes)
      futureRes
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
