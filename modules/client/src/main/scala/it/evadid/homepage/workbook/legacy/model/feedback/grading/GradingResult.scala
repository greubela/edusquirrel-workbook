package it.evadid.homepage.workbook.legacy.model.feedback.grading

import it.evadid.homepage.workbook.legacy.model.feedback.FeedbackResult

trait GradingResult[GradingState] extends FeedbackResult {
  def grade: GradingGrade

  def stateWhenStarted: GradingState
}
