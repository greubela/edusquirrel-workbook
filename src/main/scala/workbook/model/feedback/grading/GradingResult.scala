package workbook.model.feedback.grading

import workbook.model.feedback.FeedbackResult

trait GradingResult extends FeedbackResult {
  def grade: GradingGrade
}
