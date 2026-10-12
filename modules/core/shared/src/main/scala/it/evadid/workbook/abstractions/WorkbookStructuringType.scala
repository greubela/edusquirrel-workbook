package it.evadid.workbook.abstractions

import upickle.default.ReadWriter

enum WorkbookStructuringType derives ReadWriter {
  case EXERCISE_CONTAINER
  case SECTION
  case WORKBOOK
}
