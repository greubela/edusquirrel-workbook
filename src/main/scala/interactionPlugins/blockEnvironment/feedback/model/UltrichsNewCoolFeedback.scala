package interactionPlugins.blockEnvironment.feedback

import workbook.model.feedback.{FeedbackResult, FeedbackStatus}

/** Result of a single check/test. */
final case class PythonTestResult(
  name: String,
  passed: Boolean,
  expected: String,
  actual: String,
  message: Option[String]
)

/**
 * Aggregated feedback object for block-Python programs.
 * This type is the main return value towards the UI.
 */
final case class UltrichsNewCoolFeedback(
  summary: String,
  tests: Seq[PythonTestResult],
  generalHints: Seq[String],
  rawPython: String,
  status: FeedbackStatus,
  normalizedScore: Double
) extends FeedbackResult

object UltrichsNewCoolFeedback {

  /** Helper constructor with sensible defaults. */
  def empty(rawPython: String = ""): UltrichsNewCoolFeedback =
    UltrichsNewCoolFeedback(
      summary = "Es liegen noch keine Analysen vor.",
      tests = Seq.empty,
      generalHints = Seq.empty,
      rawPython = rawPython,
      status = FeedbackStatus.NOT_STARTET,
      normalizedScore = 0.0
    )
}
