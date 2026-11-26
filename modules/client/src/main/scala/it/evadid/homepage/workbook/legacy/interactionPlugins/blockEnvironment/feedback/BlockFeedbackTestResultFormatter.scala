package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.model.PythonTestResult
/**
 * Central formatting helper for test result presentation.
 */
object BlockFeedbackTestResultFormatter {

  final case class ExpectedActual(
    expectedLabel: String,
    expected: String,
    actualLabel: String,
    actual: String
  )

  def expectedActual(
    test: PythonTestResult,
    humanLanguage: HumanLanguage,
    onlyWhenFailed: Boolean = true
  ): Option[ExpectedActual] = {
    if (onlyWhenFailed && test.passed) return None

    val expected = Option(test.expected).map(_.trim).filter(_.nonEmpty)
    val actual   = Option(test.actual).map(_.trim).filter(_.nonEmpty)

    if (expected.isEmpty || actual.isEmpty) return None

    val isGerman = humanLanguage == AppLanguage.German

    if (isGenericExpected(expected.get) && isGenericActual(actual.get)) return None
    if (isGenericActual(actual.get)) return None

    val genericExp = isGenericExpected(expected.get)

    val displayExpected =
      if genericExp then
        if isGerman then "sollte fehlerfrei laufen" else "should run without error"
      else expected.get

    val displayActual = condensedError(actual.get)

    val expectedLabel = if isGerman then "Erwartet" else "Expected"
    val actualLabel   =
      if genericExp then (if isGerman then "Fehler" else "Error")
      else              (if isGerman then "Tatsächlich" else "Actual")

    Some(ExpectedActual(expectedLabel, displayExpected, actualLabel, displayActual))
  }

  private def isGenericExpected(expected: String): Boolean =
    expected.trim == "Test should pass"

  private def isGenericActual(actual: String): Boolean = {
    val a = actual.trim
    a == "Assertion failed" || a.startsWith("Assertion failed")
  }

  /** Reduces a multi-line Python traceback to its final exception line. */
  private def condensedError(actual: String): String = {
    val hasTraceback = actual.contains("Traceback") || actual.contains("Error:")
    if (!hasTraceback) return actual
    val lines = actual.replace("\r\n", "\n").split("\n").map(_.trim).filter(_.nonEmpty)
    val exceptionLine = lines.reverseIterator.find { l =>
      !l.startsWith("File \"") &&
      !l.startsWith("Traceback") &&
      !l.startsWith("During handling") &&
      !l.startsWith("The above exception") &&
      l.nonEmpty
    }
    exceptionLine.getOrElse(lines.lastOption.getOrElse(actual))
  }
}
