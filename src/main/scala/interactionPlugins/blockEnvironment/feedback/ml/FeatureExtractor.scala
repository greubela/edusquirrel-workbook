package interactionPlugins.blockEnvironment.feedback.ml

import interactionPlugins.blockEnvironment.feedback.rules.{RuleSeverity}

/**
 * Converts [[BlockFeedbackSignals]] into a numeric feature vector.
 *
 * Output is designed for simple linear models (e.g., logistic regression) later.
 */
object FeatureExtractor {

  /** Stable default feature order for `toArray`. */
  val defaultFeatureOrder: Vector[String] = Vector(
    "lines_of_code",
    "non_empty_lines",
    "blank_lines",
    "comment_lines",
    "print_count",
    "input_call_count",
    "random_call_count",
    "has_pass_statement",
    "boundary_hint_score",
    "stdout_line_count",
    "stderr_line_count",
    "tests_total",
    "tests_passed",
    "tests_failed",
    "has_runtime_error",
    "py_rules_failed_warning",
    "py_rules_failed_error",
    "vm_rules_failed_warning",
    "vm_rules_failed_error",
    // error-type lexical flags (helps reduce feature collisions)
    "err_has_traceback",
    "err_syntaxerror",
    "err_indentationerror",
    "err_nameerror",
    "err_typeerror",
    "err_valueerror",
    "err_attributeerror",
    "err_indexerror",
    "err_keyerror",
    "err_zerodivisionerror",
    "err_timeout"
  )

  def toMap(signals: BlockFeedbackSignals): Map[String, Double] = {
    val testsTotal = signals.runtimeOutcome.tests.size
    val testsPassed = signals.runtimeOutcome.tests.count(_.passed)
    val testsFailed = testsTotal - testsPassed

    val runtimeErr = signals.runtimeOutcome.runtimeError.getOrElse("")
    val stderr = signals.runtimeOutcome.stderr.getOrElse("")
    val combinedError = (runtimeErr + "\n" + stderr).toLowerCase

    def errFlag(substr: String): Double = if combinedError.contains(substr) then 1.0 else 0.0

    val hasRuntimeError = if runtimeErr.nonEmpty || combinedError.contains("traceback") then 1.0 else 0.0

    val pyFailedWarn = signals.pythonRules.count(r => !r.passed && r.severity == RuleSeverity.Warning)
    val pyFailedErr  = signals.pythonRules.count(r => !r.passed && r.severity == RuleSeverity.Error)
    val vmFailedWarn = signals.vmRules.count(r => !r.passed && r.severity == RuleSeverity.Warning)
    val vmFailedErr  = signals.vmRules.count(r => !r.passed && r.severity == RuleSeverity.Error)

    Map(
      "lines_of_code" -> signals.linesOfCode.toDouble,
      "non_empty_lines" -> signals.nonEmptyLineCount.toDouble,
      "blank_lines" -> signals.blankLineCount.toDouble,
      "comment_lines" -> signals.commentLineCount.toDouble,
      "print_count" -> signals.printCount.toDouble,
      "input_call_count" -> signals.inputCallCount.toDouble,
      "random_call_count" -> signals.randomCallCount.toDouble,
      "has_pass_statement" -> (if signals.hasPassStatement then 1.0 else 0.0),
      "boundary_hint_score" -> signals.boundaryHintScore.toDouble,
      "stdout_line_count" -> signals.stdoutLineCount.toDouble,
      "stderr_line_count" -> signals.stderrLineCount.toDouble,
      "tests_total" -> testsTotal.toDouble,
      "tests_passed" -> testsPassed.toDouble,
      "tests_failed" -> testsFailed.toDouble,
      "has_runtime_error" -> hasRuntimeError,
      "py_rules_failed_warning" -> pyFailedWarn.toDouble,
      "py_rules_failed_error" -> pyFailedErr.toDouble,
      "vm_rules_failed_warning" -> vmFailedWarn.toDouble,
      "vm_rules_failed_error" -> vmFailedErr.toDouble,
      "err_has_traceback" -> errFlag("traceback"),
      "err_syntaxerror" -> errFlag("syntaxerror"),
      "err_indentationerror" -> errFlag("indentationerror"),
      "err_nameerror" -> errFlag("nameerror"),
      "err_typeerror" -> errFlag("typeerror"),
      "err_valueerror" -> errFlag("valueerror"),
      "err_attributeerror" -> errFlag("attributeerror"),
      "err_indexerror" -> errFlag("indexerror"),
      "err_keyerror" -> errFlag("keyerror"),
      "err_zerodivisionerror" -> errFlag("zerodivisionerror"),
      "err_timeout" -> (if combinedError.contains("timed out") || combinedError.contains("timeout") then 1.0 else 0.0)
    )
  }

  def toArray(
    signals: BlockFeedbackSignals,
    featureOrder: Vector[String] = defaultFeatureOrder
  ): Array[Double] = {
    val features = toMap(signals)
    featureOrder.map(key => features.getOrElse(key, 0.0)).toArray
  }
}
