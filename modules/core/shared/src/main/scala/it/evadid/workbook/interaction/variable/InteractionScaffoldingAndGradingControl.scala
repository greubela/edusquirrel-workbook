package it.evadid.workbook.interaction.variable

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.storage.AsyncDataCache
import it.evadid.core.datastructures.storage.CacheElementStatus.{FAILED, STARTED, UNKNOWN}
import it.evadid.distribution.command.SerializedException
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.workbook.abstractions.FeedbackEntity.SystemEntity
import it.evadid.workbook.abstractions.grading.{GradedState, GradingStatus}
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.meta.ExecutionContextInformation

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case class InteractionScaffoldingAndGradingControl[T, GA, SA](interactionVariable: InteractionVariable[T, GA, SA]) {
  // Context supplies the loader's services; only state, language and force mode identify a cache entry.
  private case class GradingRequest(state: T, language: HumanLanguage, forced: Boolean)(val context: ExecutionContextInformation)
  private case class ScaffoldingRequest(state: T, language: HumanLanguage)(val context: ExecutionContextInformation)
  private val logger = Logger.withNameAndPrefixes(None, PrintToStdLogger.printNothing)

  def getCurrentStateOfGrading(): Option[GradedState[T, GA]] = interactionVariable.synchronized {
    interactionVariable.currentHistory.historyStates.lastStateOption.map { current =>
      val cached = gradingCache.cacheCopy().collect {
        case (request, result) if request.state == current.value && request.language == request.context.getCurrentLanguage => result
      }.maxByOption(_.timestampLatestTest)
      val saved = interactionVariable.currentHistory.historyGrading.gradingsMap.get(current.value)
        .filter(_.state == current.value).orElse(cached).getOrElse(GradedState[T, GA](current.value))
      val pending = gradingCache.statusSnapshot.exists { (request, status) =>
        request.state == current.value && request.language == request.context.getCurrentLanguage && status == STARTED()
      }
      saved.fillMapForUnavailable(interactionVariable.underlyingInteraction,
        if pending then GradingStatus.NotFinished() else GradingStatus.NotRequested())
    }
  }

  def getCurrentStateOfScaffolding(): Option[ScaffoldingResult[T, SA]] = interactionVariable.synchronized {
    interactionVariable.currentHistory.historyStates.lastStateOption.flatMap { current =>
      val cached = scaffoldingCache.cacheCopy().collect {
        case (request, result) if request.state == current.value && request.language == request.context.getCurrentLanguage => result
      }.maxByOption(_.scaffoldingTime)
      interactionVariable.currentHistory.historyScaffolding.scaffoldingMap.get(current.value).orElse(cached).orElse {
        val statuses = scaffoldingCache.statusSnapshot.collect {
          case (request, status) if request.state == current.value && request.language == request.context.getCurrentLanguage => status
        }
        if statuses.exists(_ == STARTED()) then Some(ScaffoldingResult(current.value,
          ScaffoldingStatus.ScaffoldingRequestedButNotFinished(), SystemEntity(), None))
        else statuses.collectFirst { case FAILED(error) =>
          ScaffoldingResult(current.value, ScaffoldingStatus.ScaffoldingError(error), SystemEntity(), None)
        }
      }
    }
  }

  private def reusableGrading(request: GradingRequest): GradedState[T, GA] = {
    val saved = interactionVariable.currentHistory.historyGrading.gradingsMap.get(request.state)
      .filter(_.state == request.state).orElse(gradingCache.getSyncIfInCache(request, loadIfNotPresent = false))
    val graders = interactionVariable.underlyingInteraction.gradingElements
    GradedState(request.state, saved.toList.flatMap(_.graderResultMap).filter { (grader, result) =>
      graders.contains(grader) && result.grader == grader && result.gradedState == request.state && grader.canReuseResult(result, request.context)
    }.toMap)
  }

  def getOrCalculateGrading(contextInformation: ExecutionContextInformation, forceGrading: Boolean = false): Future[GradedState[T, GA]] =
    interactionVariable.currentHistory.historyStates.lastStateOption match {
      case Some(current) => getOrCalculateGrading(contextInformation, forceGrading, current.value)
      case None => Future.failed(SerializedException("Cannot grade an interaction without a current state."))
    }

  def getOrCalculateGrading(contextInformation: ExecutionContextInformation, forceGrading: Boolean, forState: T): Future[GradedState[T, GA]] = interactionVariable.synchronized {
    val request = GradingRequest(forState, contextInformation.getCurrentLanguage, forceGrading)(contextInformation)
    val status = gradingCache.getCurrentElementStatus(request)
    if status == STARTED() then gradingCache.loadAsFuture(request)(using contextInformation.futureExecutionContext)
    else {
      val reusable = reusableGrading(request)
      if interactionVariable.underlyingInteraction.gradingElements.forall(reusable.graderResultMap.contains) then
        Future.fromTry(Try {
          if !interactionVariable.currentHistory.historyGrading.gradingsMap.contains(forState) && reusable.testsWithResults.nonEmpty then
            interactionVariable.updateHistory(_.addGrading(reusable), contextInformation)
          reusable
        })
      else gradingCache.loadAsFuture(request, forceReloading = status != UNKNOWN())(using contextInformation.futureExecutionContext)
    }
  }

  private def startGrading(request: GradingRequest): Future[GradedState[T, GA]] = {
    val reusable = reusableGrading(request)
    val pending = interactionVariable.underlyingInteraction.gradingElements.distinct.filterNot(reusable.graderResultMap.contains)
    GradedState.executeGradingSafe(pending, request.state, request.forced, request.context).map { result =>
      val combined = GradedState(request.state, reusable.graderResultMap ++ result.graderResultMap)
      interactionVariable.synchronized {
        if request.context.getCurrentLanguage == request.language then
          interactionVariable.updateHistory(_.addGrading(combined), request.context)
        else gradingCache.removeFromCache(List(request))
      }
      combined
    }(using request.context.futureExecutionContext)
  }

  def getOrCalculateScaffolding(contextInformation: ExecutionContextInformation): Future[ScaffoldingResult[T, SA]] =
    interactionVariable.currentHistory.historyStates.lastStateOption match {
      case Some(current) => getOrCalculateScaffolding(contextInformation, current.value)
      case None => Future.failed(SerializedException("Cannot request scaffolding without a current state."))
    }

  def getOrCalculateScaffolding(contextInformation: ExecutionContextInformation, forState: T): Future[ScaffoldingResult[T, SA]] = interactionVariable.synchronized {
    val request = ScaffoldingRequest(forState, contextInformation.getCurrentLanguage)(contextInformation)
    val status = scaffoldingCache.getCurrentElementStatus(request)
    if status == STARTED() then scaffoldingCache.loadAsFuture(request)(using contextInformation.futureExecutionContext)
    else {
      val saved = interactionVariable.currentHistory.historyScaffolding.scaffoldingMap.get(forState)
        .orElse(scaffoldingCache.getSyncIfInCache(request, loadIfNotPresent = false)).filter { result =>
          result.forState == forState && (result.scaffoldingStatus match {
            case ScaffoldingStatus.ScaffoldingFeedback(feedback) => feedback.tryGetInLanguage(request.language).nonEmpty
            case _ => false
          })
        }
      saved.map { result => Future.fromTry(Try {
        if !interactionVariable.currentHistory.historyScaffolding.scaffoldingMap.contains(forState) then
          interactionVariable.updateHistory(_.addScaffolding(result), contextInformation)
        result
      }) }.getOrElse(scaffoldingCache.loadAsFuture(request, forceReloading = status != UNKNOWN())(using contextInformation.futureExecutionContext))
    }
  }

  private def startScaffolding(request: ScaffoldingRequest): Future[ScaffoldingResult[T, SA]] =
    interactionVariable.underlyingInteraction.scaffoldingElement match {
      case None => Future.failed(SerializedException("This interaction has no scaffolder."))
      case Some(scaffolder) => scaffolder.generateFeedbackSafe(request.state, request.context).map { result =>
        interactionVariable.synchronized {
          if request.context.getCurrentLanguage == request.language then
            interactionVariable.updateHistory(_.addScaffolding(result), request.context)
          else scaffoldingCache.removeFromCache(List(request))
        }
        result
      }(using request.context.futureExecutionContext)
    }

  private val gradingCache = new AsyncDataCache[GradingRequest, GradedState[T, GA]](logger) {
    override protected def executeLoading(request: GradingRequest)(ec: ExecutionContext): Future[GradedState[T, GA]] = startGrading(request)
    override protected def formatInputForLogging(request: GradingRequest): String = request.toString
    override protected def formatOutputForLogging(result: GradedState[T, GA]): String = result.toString
  }

  private val scaffoldingCache = new AsyncDataCache[ScaffoldingRequest, ScaffoldingResult[T, SA]](logger) {
    override protected def executeLoading(request: ScaffoldingRequest)(ec: ExecutionContext): Future[ScaffoldingResult[T, SA]] = startScaffolding(request)
    override protected def formatInputForLogging(request: ScaffoldingRequest): String = request.toString
    override protected def formatOutputForLogging(result: ScaffoldingResult[T, SA]): String = result.toString
  }
}
