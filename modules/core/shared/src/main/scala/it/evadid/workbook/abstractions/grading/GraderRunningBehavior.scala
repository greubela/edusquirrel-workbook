package it.evadid.workbook.abstractions.grading

import upickle.default.*

enum GraderRunningBehavior derives ReadWriter {
  case RUN_ALWAYS
  case RUN_ONLY_MANUALLY
}
