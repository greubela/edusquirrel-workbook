package it.evadid.workbook.model.feedback

/** Common data contract; implementations store their original timestamp explicitly. */
trait FeedbackResult {
  def timestampEpochMillis: Long
  def status: FeedbackStatus
}
