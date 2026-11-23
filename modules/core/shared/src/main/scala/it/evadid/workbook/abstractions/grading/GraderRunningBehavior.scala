package it.evadid.workbook.abstractions.grading

import it.evadid.workbook.interaction.sync.UpdateImportance
import upickle.default.*

/** When a grader runs without an explicit request (e.g. the check button, which always runs it). */
enum GraderRunningBehavior derives ReadWriter {
  /** After every change of the answer. */
  case RUN_ALWAYS
  /** After changes of at least MINOR importance; DEFAULT and TEMPORARY answers (e.g. mid-word typing) are not graded. */
  case RUN_ON_AT_LEAST_MINOR
  case RUN_ONLY_MANUALLY

  def runsAutomatically(importance: UpdateImportance): Boolean = this match {
    case RUN_ALWAYS => true
    case RUN_ON_AT_LEAST_MINOR => importance.ordinal >= UpdateImportance.MINOR.ordinal
    case RUN_ONLY_MANUALLY => false
  }
}
