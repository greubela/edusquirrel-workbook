package it.evadid.workbook.abstractions.grading

import it.evadid.distribution.command.SerializedException
import it.evadid.util.MapUtils
import it.evadid.workbook.abstractions.FeedbackEntity.LlmEntity
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElementFull}
import it.evadid.workbook.abstractions.grading.GradingStatus.*
import it.evadid.workbook.meta.ExecutionContextInformation
import upickle.default.*

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

case class GradedState[F <: InteractionFormat]
(
  state: F#InteractionStateType,
  graderResultMap: Map[WorkbookInteractionGrader[F], GradingResult[F]]
) derives ReadWriter {

  lazy val testsWithResults: Set[GradingResult[F]] = graderResultMap.values.toSet
  val timestampLatestTest: LocalDateTime = testsWithResults.map(_.gradingTime).maxOption.getOrElse(LocalDateTime.MIN)

  lazy val shouldRetry: Seq[WorkbookInteractionGrader[F]] = graderResultMap.toList.filter(_._2.gradingStatus.shouldRetryExecution).map(_._1)

  def doesEvaluationRelyOnLlm(): Boolean = {
    graderResultMap.values.exists(_.grader.gradingEntity == LlmEntity())
  }

  def totalEvaluation(): GradingStatus = {
    val gradingResults = graderResultMap.values
    val notRequested = gradingResults.count(_.gradingStatus == NotRequested())
    val notFinished = gradingResults.map(_.gradingStatus).count(_ == NotFinished())
    val testError = gradingResults.map(_.gradingStatus).count(_.isError)
    val shouldRetry = gradingResults.map(_.gradingStatus).count(_.shouldRetryExecution)
    val correct = gradingResults.count(_.gradingStatus.isCorrect)
    val incorrect = gradingResults.size - correct

    if (notRequested > 0) NotRequested()
    else if (notFinished > 0) NotFinished()
    else if (testError > 0 || shouldRetry > 0) TestError(SerializedException(s"There are ${testError} tests with error (${shouldRetry} should be retries)"))
    else if (correct == 0 && incorrect > 0) Incorrect()
    else if (correct > 0 && incorrect == 0) Correct()
    else PartiallyCorrect()

  }

  def retryFailed(contextInformation: ExecutionContextInformation, forceGrading: Boolean): Future[GradedState[F]] = {
    if (!forceGrading || shouldRetry.isEmpty) Future.successful(this)
    else {
      GradedState
        .executeGradingSafe(shouldRetry, state, forceGrading, contextInformation)
        .map(_.merge(this))(using contextInformation.futureExecutionContext)
    }
  }

  def fillMapForUnavailable(interaction: WorkbookInteractionElementFull[T, A, ?], gradingStatusOfUnavailable: GradingStatus): GradedState[F] = {
    def ifUnavailable(grader: WorkbookInteractionGrader[F]): GradingResult[F] = GradingResult(grader, state, gradingStatusOfUnavailable, None, None, LocalDateTime.now())

    val fullMap = interaction.gradingElements.map(curGradingElement => {
      curGradingElement -> graderResultMap.getOrElse(curGradingElement, ifUnavailable(curGradingElement))
    }).toMap
    GradedState[F](state, fullMap)
  }

  def merge(other: GradedState[F]): GradedState[F] = {
    val merged = MapUtils.mergeMaps[WorkbookInteractionGrader[F], GradingResult[F]](graderResultMap, other.graderResultMap, (k, v1, v2) => v1.merge(v2))
    GradedState(state, merged)
  }

}

object GradedState {

  def apply[F](state: T) = new GradedState[F](state, Map())

  def executeGradingSafe[F](graders: IterableOnce[WorkbookInteractionGrader[F]], state: T, forceGrading: Boolean, context: ExecutionContextInformation): Future[GradedState[F]] = {
    given ExecutionContext = context.futureExecutionContext

    val graderRes: Future[Seq[GradingResult[F]]] = Future.traverse(graders.iterator.toList.distinctBy(_.key))(curGrader => curGrader.gradeStateSafe(state, forceGrading, context))
    graderRes.map(graderResults => {
      GradedState(state, graderResults.map(curRes => curRes.grader -> curRes).toMap)
    })(using context.futureExecutionContext)
  }

}
