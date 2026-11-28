package it.evadid.workbook.abstractions

import upickle.default.ReadWriter

enum WorkbookStructuringType derives ReadWriter {
  @upickle.implicits.key("EXERCISE_CONTAINER")
  case EXERCISE_CONTAINER
  @upickle.implicits.key("SECTION")
  case SECTION
  @upickle.implicits.key("WORKBOOK")
  case WORKBOOK
}
