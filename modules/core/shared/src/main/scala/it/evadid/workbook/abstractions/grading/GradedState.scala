package it.evadid.workbook.abstractions.grading

import it.evadid.util.MapUtils

import java.time.Instant

/** The results of an answer's graders; computed by the feedback control. */
case class GradedState[T, GA]
(
  state: T,
  graderResultMap: Map[WorkbookInteractionGrader[T, GA], GradingResult[T, GA]]
) {

  lazy val testsWithResults: Set[GradingResult[T, GA]] = graderResultMap.values.toSet
  val timestampLatestTest: Instant = testsWithResults.map(_.gradingTime).maxOption.getOrElse(Instant.MIN)

  def totalEvaluation(): GradingStatus =
    it.evadid.workbook.reports.GradingSummary(graderResultMap.keys.toSeq, this).status

  def fillMapForUnavailable(gradingElements: Seq[WorkbookInteractionGrader[T, GA]], gradingStatusOfUnavailable: GradingStatus): GradedState[T, GA] = {
    def ifUnavailable(grader: WorkbookInteractionGrader[T, GA]): GradingResult[T, GA] = GradingResult(grader, state, gradingStatusOfUnavailable, None, None, Instant.now())

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

  def apply[T, GA](state: T) = new GradedState[T, GA](state, Map())
}
