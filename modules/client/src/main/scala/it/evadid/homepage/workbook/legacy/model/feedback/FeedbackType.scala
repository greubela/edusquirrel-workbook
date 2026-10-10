package it.evadid.homepage.workbook.legacy.model.feedback

enum FeedbackType derives upickle.default.ReadWriter {
  case AI_GENERATED, TEACHER_MANUAL, UNIT_TESTS
}