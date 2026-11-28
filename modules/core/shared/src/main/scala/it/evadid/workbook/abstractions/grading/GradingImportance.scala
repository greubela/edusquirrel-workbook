package it.evadid.workbook.abstractions.grading

import upickle.default.*

enum GradingImportance derives ReadWriter{
  @upickle.implicits.key("HIDDEN_TEST") case HIDDEN_TEST
  @upickle.implicits.key("REQUIRED_TEST") case REQUIRED_TEST
  @upickle.implicits.key("OPTIONAL_TEST") case OPTIONAL_TEST
}
