package workbook.model.feedback

import java.time.{Instant, LocalDateTime, ZoneOffset}
import scala.scalajs.js

trait FeedbackResult {
  val timestampEpochMillis: Long = (new js.Date()).getTime().toLong

  def status: FeedbackStatus
}
