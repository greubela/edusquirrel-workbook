package workbook.model.feedback.grading

import workbook.model.feedback.FeedbackStatus


case class BasicVariableGradingResult[T](variable: T, status: FeedbackStatus, grade: GradingGrade) extends GradingResult {
}

type BasicStringGradingResult = BasicVariableGradingResult[String]
