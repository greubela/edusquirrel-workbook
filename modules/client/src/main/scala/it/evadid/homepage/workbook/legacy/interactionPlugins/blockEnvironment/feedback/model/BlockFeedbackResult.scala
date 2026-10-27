package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.model

import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.BlockFeedbackTestResultFormatter
import it.evadid.homepage.workbook.legacy.model.feedback.{FeedbackResult, FeedbackStatus}

/** UI-friendly representation for test display. */
final case class FeedbackTestDisplay(
  name: String,
  passed: Boolean,
  message: String,
  expectedActual: Option[BlockFeedbackTestResultFormatter.ExpectedActual]
) derives upickle.default.ReadWriter

/** Result of a single check/test. */
final case class PythonTestResult(
  name: String,
  passed: Boolean,
  expected: String,
  actual: String,
  message: Option[String]
) derives upickle.default.ReadWriter

/** Aggregated analysis data and presentation-ready feedback for block-Python programs. */
final case class BlockFeedbackResult(
  summary: String,
  tests: Seq[PythonTestResult],
  generalHints: Seq[String],
  displayHints: Seq[String] = Seq.empty,
  displayTests: Seq[FeedbackTestDisplay] = Seq.empty,
  allTestsPassed: Boolean = false,
  rawPython: String,
  status: FeedbackStatus,
  normalizedScore: Double,
  debug: Option[FeedbackDebug] = None,
  override val timestampEpochMillis: Long = System.currentTimeMillis()
) extends FeedbackResult derives upickle.default.ReadWriter

final case class FeedbackDebug(
  llmEligible: Boolean,
  llmProxyAttempted: Boolean,
  aiHintAdded: Boolean,
  planHintsCount: Int,
  ruleHintsCount: Int,
  runtimeHintsCount: Int,
  testsTotal: Int,
  testsFailed: Int,
  hasRuntimeError: Boolean,
  hasEmptySource: Boolean,
  primaryIssue: String,
  templateId: Option[String],
  /** Number of correction prompts sent back to the LLM after QualityGate rejections (0 = clean first try). */
  llmRewriteCount: Int = 0,
  /** QualityGate reason codes from the final LLM attempt (empty when gate passed). */
  llmLastGateReasons: Seq[String] = Seq.empty,
  /** Detected function-name renames */
  functionNameMismatch: Seq[String] = Seq.empty,
  /** Raw runtime error text before LLM rephrasing (empty when no runtime error). */
  rawRuntimeError: Option[String] = None
) derives upickle.default.ReadWriter

object BlockFeedbackResult {

  /** Helper constructor with sensible defaults. */
  def empty(rawPython: String = ""): BlockFeedbackResult =
    BlockFeedbackResult(
      summary = "Es liegen noch keine Analysen vor.",
      tests = Seq.empty,
      generalHints = Seq.empty,
      displayHints = Seq.empty,
      displayTests = Seq.empty,
      allTestsPassed = false,
      rawPython = rawPython,
      status = FeedbackStatus.NOT_STARTET,
      normalizedScore = 0.0,
      debug = None
    )
}
