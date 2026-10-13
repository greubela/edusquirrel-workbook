package it.evadid.workbook.model.feedback

enum FeedbackStatus derives upickle.default.ReadWriter {
  case NOT_STARTET, IN_PROGRESS, FINISHED
}