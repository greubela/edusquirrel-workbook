package it.evadid.workbook.abstractions.grading

import it.evadid.workbook.sync.UpdateImportance
import upickle.default.*

/** When a grader runs without an explicit request (e.g. the check button, which always runs it). */
enum GraderRunningBehavior derives ReadWriter {
  /** After every change of the answer. */
  @upickle.implicits.key("RUN_ALWAYS")
  case RUN_ALWAYS
  /** After changes of at least MINOR importance; DEFAULT and TEMPORARY answers (e.g. mid-word typing) are not graded. */
  @upickle.implicits.key("RUN_ON_AT_LEAST_MINOR")
  case RUN_ON_AT_LEAST_MINOR
  @upickle.implicits.key("RUN_ONLY_MANUALLY")
  case RUN_ONLY_MANUALLY

  def runsAutomatically(importance: UpdateImportance): Boolean = this match {
    case RUN_ALWAYS => true
    case RUN_ON_AT_LEAST_MINOR => importance.ordinal >= UpdateImportance.MINOR.ordinal
    case RUN_ONLY_MANUALLY => false
  }
}
