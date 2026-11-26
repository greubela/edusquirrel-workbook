package it.evadid.workbook.abstractions.grading

import it.evadid.workbook.interaction.sync.UpdateImportance

import it.evadid.distribution.command.SerializedException
import it.evadid.util.MapUtils
import it.evadid.workbook.abstractions.FeedbackEntity.LlmEntity
import it.evadid.workbook.abstractions.{InteractionFormat, *}
import it.evadid.workbook.abstractions.grading.GradingStatus.*
import it.evadid.workbook.meta.ExecutionContextInformation
import upickle.default.*

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

case class GradedState[T, GA]
(
  state: T,
  graderResultMap: Map[WorkbookInteractionGrader[T, GA], GradingResult[T, GA]]
) derives ReadWriter {

  lazy val testsWithResults: Set[GradingResult[T, GA]] = graderResultMap.values.toSet
  val timestampLatestTest: LocalDateTime = testsWithResults.map(_.gradingTime).maxOption.getOrElse(LocalDateTime.MIN)

  lazy val shouldRetry: Seq[WorkbookInteractionGrader[T, GA]] = graderResultMap.toList.filter(_._2.gradingStatus.shouldRetryExecution).map(_._1)

  def doesEvaluationRelyOnLlm(): Boolean = {
    graderResultMap.values.exists(_.grader.gradingEntity == LlmEntity)
  }

  def totalEvaluation(): GradingStatus =
    it.evadid.workbook.meta.GradingSummary(graderResultMap.keys.toSeq, this).status

  def retryFailed(contextInformation: ExecutionContextInformation, forceGrading: Boolean, importance: UpdateImportance): Future[GradedState[T, GA]] = {
    if (!forceGrading || shouldRetry.isEmpty) Future.successful(this)
    else {
      GradedState
        .executeGradingSafe(shouldRetry, state, forceGrading, importance, contextInformation)
        .map(_.merge(this))(using contextInformation.futureExecutionContext)
    }
  }

  def fillMapForUnavailable(gradingElements: Seq[WorkbookInteractionGrader[T, GA]], gradingStatusOfUnavailable: GradingStatus): GradedState[T, GA] = {
    def ifUnavailable(grader: WorkbookInteractionGrader[T, GA]): GradingResult[T, GA] = GradingResult(grader, state, gradingStatusOfUnavailable, None, None, LocalDateTime.now())

    val fullMap = gradingElements.map(curGradingElement => {
      curGradingElement -> graderResultMap.getOrElse(curGradingElement, ifUnavailable(curGradingElement))
    }).toMap
    GradedState[T, GA](state, fullMap)
  }

  def merge(other: GradedState[T, GA]): GradedState[T, GA] = {
    val merged = MapUtils.mergeMaps[WorkbookInteractionGrader[T, GA], GradingResult[T, GA]](graderResultMap, other.graderResultMap, (k, v1, v2) => v1.merge(v2))
    GradedState(state, merged)
  }

}

object GradedState {

  def apply[T, GA](state:T) = new GradedState[T, GA](state, Map())

  def executeGradingSafe[T, GA](graders: IterableOnce[WorkbookInteractionGrader[T, GA]], state:T, forceGrading: Boolean, importance: UpdateImportance, context: ExecutionContextInformation): Future[GradedState[T, GA]] = {
    given ExecutionContext = context.futureExecutionContext

    val graderRes: Future[Seq[GradingResult[T, GA]]] = Future.traverse(graders.iterator.toList.distinctBy(_.key))(curGrader => curGrader.gradeStateSafe(state, forceGrading, importance, context))
    graderRes.map(graderResults => {
      GradedState(state, graderResults.map(curRes => curRes.grader -> curRes).toMap)
    })(using context.futureExecutionContext)
  }

}
