package workbook.model.feedback.grading

import workbook.model.feedback.FeedbackResult

trait GradingResult[GradingState] extends FeedbackResult {
  def grade: GradingGrade

  def stateWhenStarted: GradingState
}
