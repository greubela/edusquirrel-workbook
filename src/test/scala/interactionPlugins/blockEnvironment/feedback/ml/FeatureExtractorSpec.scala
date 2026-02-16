package interactionPlugins.blockEnvironment.feedback.ml

import interactionPlugins.blockEnvironment.feedback.PythonRuntimeOutcome
import munit.FunSuite

final class FeatureExtractorSpec extends FunSuite {

  test("defaultFeatureOrder is stable") {
    val expected = Vector(
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

    assertEquals(FeatureExtractor.defaultFeatureOrder, expected)
    assertEquals(FeatureExtractor.defaultFeatureOrder.distinct.size, FeatureExtractor.defaultFeatureOrder.size)
  }

  test("toArray follows defaultFeatureOrder") {
    val outcome = PythonRuntimeOutcome(
      tests = Seq.empty,
      runStatus = None,
      normalizedScore = None,
      runtimeError = Some("boom"),
      stdout = Some("a\n"),
      stderr = Some("b\n")
    )

    val signals = BlockFeedbackSignals(
      rawPython = "print('x')\n# c\n\n",
      pythonRules = Seq.empty,
      vmRules = Seq.empty,
      runtimeOutcome = outcome,
      linesOfCode = 1,
      nonEmptyLineCount = 2,
      commentLineCount = 1,
      blankLineCount = 1,
      printCount = 1,
      stdoutLineCount = 1,
      stderrLineCount = 1
    )

    val asMap = FeatureExtractor.toMap(signals)
    val asArr = FeatureExtractor.toArray(signals)

    assertEquals(asArr.length, FeatureExtractor.defaultFeatureOrder.length)
    FeatureExtractor.defaultFeatureOrder.zipWithIndex.foreach { case (key, idx) =>
      assertEquals(asArr(idx), asMap.getOrElse(key, 0.0))
    }

    // quick sanity checks for a few keys
    assertEquals(asMap("has_runtime_error"), 1.0)
    assertEquals(asMap("stdout_line_count"), 1.0)
    assertEquals(asMap("stderr_line_count"), 1.0)

  }
}
