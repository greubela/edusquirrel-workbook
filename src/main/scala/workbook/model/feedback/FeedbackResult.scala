package workbook.model.feedback

import java.time.LocalDateTime

trait FeedbackResult {
  val timestamp: LocalDateTime = LocalDateTime.now()

  def status: FeedbackStatus
}
