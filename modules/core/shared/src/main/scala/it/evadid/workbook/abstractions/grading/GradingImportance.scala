package it.evadid.workbook.abstractions.grading

import upickle.default.*

enum GradingImportance derives ReadWriter{
  case HIDDEN_TEST, REQUIRED_TEST, OPTIONAL_TEST
}
