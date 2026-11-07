package it.evadid.workbook.interaction.variable

import it.evadid.util.MapUtils
import it.evadid.workbook.abstractions.grading.{GradedState, GradingResult, WorkbookInteractionGrader}
import upickle.default.*

case class InteractionVariableGradingHistory[T, GA]
(
  gradingsMap: Map[T, GradedState[T, GA]]
) {

  lazy val gradingsFinished: Set[GradedState[T, GA]] = gradingsMap.values.toSet

  private[interaction] def merge(other: InteractionVariableGradingHistory[T, GA]): InteractionVariableGradingHistory[T, GA] = {
    val mergedMaps = MapUtils.mergeMaps[T, GradedState[T, GA]](gradingsMap, other.gradingsMap, (k, v1, v2) => v1.merge(v2))
    InteractionVariableGradingHistory[T, GA](gradingsMap)
  }

  private[interaction] def get(state: T): Option[GradedState[T, GA]] = gradingsMap.get(state)

}

object InteractionVariableGradingHistory {

  given [T: ReadWriter, GA: ReadWriter]: ReadWriter[InteractionVariableGradingHistory[T, GA]] = macroRW

  def apply[T, GA](gradingsFinished: Set[GradedState[T, GA]]): InteractionVariableGradingHistory[T, GA] = {
    val resMap = gradingsFinished.map(curGrad => curGrad.state -> curGrad).toMap
    InteractionVariableGradingHistory(resMap)
  }

  def forGratedState[T, GA](gradedState: Set[GradedState[T, GA]]): InteractionVariableGradingHistory[T, GA] = {
    InteractionVariableGradingHistory(gradedState.map(cur => cur.state -> cur).toMap)
  }

  def forSingleGradings[T, GA](singleGradings: Set[GradingResult[T, GA]]): InteractionVariableGradingHistory[T, GA] = {
    val map: Map[T, GradedState[T, GA]] = singleGradings.map(curGrading => {
      curGrading.gradedState -> curGrading
    }).groupBy(_._1).map((curState, gradingList) => {
      val graderMap: Map[WorkbookInteractionGrader[T, GA], GradingResult[T, GA]] = gradingList.map(gradingResult => gradingResult._2.grader -> gradingResult._2).toMap
      curState -> GradedState(curState, graderMap)
    }).toMap
    InteractionVariableGradingHistory(map)

  }


}
