package it.evadid.workbook.abstractions.grading

import upickle.default.*

enum GradingStatus derives ReadWriter {
  case CORRECT, PARTIALLY_CORRECT, INCORRECT, TEST_ERROR, NOT_EXECUTED
}

