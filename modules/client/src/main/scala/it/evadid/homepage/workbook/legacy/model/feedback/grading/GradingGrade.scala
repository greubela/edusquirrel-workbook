package it.evadid.homepage.workbook.legacy.model.feedback.grading

enum GradingGrade derives upickle.default.ReadWriter {
  case UNKNOWN, GRADING_ERROR, CORRECT, INCORRECT, PARTIALLY_CORRECT
}