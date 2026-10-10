package it.evadid.workbook.abstractions.grading

import upickle.default.*

enum GradingGrade derives ReadWriter {
  case CORRECT, PARTIALLY_CORRECT, INCORRECT
}

