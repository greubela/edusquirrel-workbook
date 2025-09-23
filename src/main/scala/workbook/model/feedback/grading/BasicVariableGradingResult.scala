package workbook.model.feedback.grading

import workbook.model.feedback.FeedbackStatus
import workbook.model.states.{InteractionState, Stateless}


case class BasicVariableGradingResult[T, GradingState <: InteractionState](stateWhenStarted: GradingState, variable: T, status: FeedbackStatus, grade: GradingGrade) extends GradingResult[GradingState] {
}

type GptGradingResult = BasicVariableGradingResult[String, Stateless]
