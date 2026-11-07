package it.evadid.workbook.abstractions.grading

import it.evadid.util.MapUtils
import it.evadid.workbook.abstractions.WorkbookInteractionElementFull
import it.evadid.workbook.meta.ExecutionContextInformation
import upickle.default.*

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

case class GradedState[T, A]
(
  state: T,
  graderResultMap: Map[WorkbookInteractionGrader[T, A], GradingResult[T, A]]
) derives ReadWriter {

  lazy val testsWithResults: Set[GradingResult[T, A]] = graderResultMap.values.toSet
  val timestampLatestTest: LocalDateTime = testsWithResults.map(_.gradingTime).maxOption.getOrElse(LocalDateTime.MIN)


  def fillMapForUnavailable(interaction: WorkbookInteractionElementFull[T, A, ?], gradingStatusOfUnavailable: GradingStatus): GradedState[T, A] = {

    def ifUnavailable(grader: WorkbookInteractionGrader[T, A]): GradingResult[T, A] = GradingResult(grader, state, gradingStatusOfUnavailable, None, None, LocalDateTime.now())

    val fullMap = interaction.gradingElements.map(curGradingElement => {
      curGradingElement -> graderResultMap.getOrElse(curGradingElement, ifUnavailable(curGradingElement))
    }).toMap
    GradedState[T, A](state, fullMap)
  }

  def merge(other: GradedState[T, A]): GradedState[T, A] = {
    val merged = MapUtils.mergeMaps[WorkbookInteractionGrader[T, A], GradingResult[T, A]](graderResultMap, other.graderResultMap, (k, v1, v2) => v1.merge(v2))
    GradedState(state, merged)
  }

}

object GradedState {

  def apply[T, A](state: T) = new GradedState[T, A](state, Map())


  def executeGradingSafe[T, A](graders: Seq[WorkbookInteractionGrader[T, A]], state: T, forceGrading: Boolean, context: ExecutionContextInformation): Future[GradedState[T, A]] = {
    given ExecutionContext  = context.futureExecutionContext
    val graderRes: Future[Seq[GradingResult[T, A]]] = Future.traverse(graders)(curGrader => curGrader.gradeStateSafe(state, forceGrading, context))
    graderRes.map(graderResults => {
      GradedState(state, graderResults.map(curRes => curRes.grader -> curRes).toMap)
    })(using context.futureExecutionContext)
  }

}
