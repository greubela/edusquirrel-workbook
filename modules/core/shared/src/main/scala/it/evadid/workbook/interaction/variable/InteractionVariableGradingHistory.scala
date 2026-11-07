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
    def valid(history: Map[T, GradedState[T, GA]]) = history.collect {
      case (state, group) if group.state == state => state -> group.copy(
        graderResultMap = group.graderResultMap.filter((grader, result) => result.grader == grader && result.gradedState == state))
    }
    val mergedMaps = MapUtils.mergeMaps[T, GradedState[T, GA]](valid(gradingsMap), valid(other.gradingsMap), (k, v1, v2) => v1.merge(v2))
    InteractionVariableGradingHistory[T, GA](mergedMaps)
  }

  private[interaction] def get(state: T): Option[GradedState[T, GA]] = gradingsMap.get(state)

}

object InteractionVariableGradingHistory {

  given [T: ReadWriter, GA: ReadWriter]: ReadWriter[InteractionVariableGradingHistory[T, GA]] = macroRW

  def apply[T, GA](gradingsFinished: Set[GradedState[T, GA]]): InteractionVariableGradingHistory[T, GA] =
    new InteractionVariableGradingHistory(gradingsFinished.groupBy(_.state).view.mapValues(_.reduce(_.merge(_))).toMap)

  def fromResults[T, GA](results: Iterable[GradingResult[T, GA]]): InteractionVariableGradingHistory[T, GA] = {
    val grouped = results.groupBy(_.gradedState).map { (state, grades) =>
      val byGrader = grades.groupBy(_.grader).view.mapValues(_.reduce(_.merge(_))).toMap
      state -> GradedState(state, byGrader)
    }
    new InteractionVariableGradingHistory(grouped)
  }
}
