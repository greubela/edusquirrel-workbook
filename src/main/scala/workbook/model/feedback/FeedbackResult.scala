package workbook.model.feedback

import java.time.{Instant, LocalDateTime, ZoneOffset}
import scala.scalajs.js

trait FeedbackResult {
  val timestampEpochMillis: Long = (new js.Date()).getTime().toLong


  /*{
    val epochMillis =
    val res = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)
    res
  }*/

  def status: FeedbackStatus
}
