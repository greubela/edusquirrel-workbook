package interactionPlugins.blockEnvironment.feedback.rules

import scala.collection.mutable
import scala.util.matching.Regex

/**
 * Static rules operating directly on the Python source text.
 *
 * Works purely textually to remain robust and fast.
 */
object PythonStaticRules {

  private val MaxRecommendedLineLength      = 100
  private val MaxRecommendedBlankStreak     = 2
  private val MaxRecommendedPrintStatements = 5

  def runAll(rawCode: String): Seq[RuleResult] = {
    val normalized = Option(rawCode).getOrElse("").replace("\r\n", "\n")
    val trimmed    = normalized.trim

    val results = mutable.ListBuffer.empty[RuleResult]

    if trimmed.isEmpty then {
      results += RuleResult(
        id = "PY_EMPTY",
        category = "PYTHON_STRUCTURE",
        severity = RuleSeverity.Error,
        passed = false,
        message = "Es wurde kein Python-Code gefunden. Füge zunächst eine Lösung ein, bevor weitere Analysen sinnvoll sind.",
        details = Some("Leerer oder nur aus Whitespace bestehender Code.")
      )
      return results.toList
    }

    // Code is non-empty
    results += RuleResult(
      id = "PY_NON_EMPTY",
      category = "PYTHON_STRUCTURE",
      severity = RuleSeverity.Info,
      passed = true,
      message = "Es liegt ausführbarer Python-Code vor.",
      details = None
    )

    val lines = normalized.split("\n", -1).toIndexedSeq

    // Line count (only non-empty lines)
    val nonEmptyLineCount = lines.count(_.trim.nonEmpty)
    results += RuleResult(
      id = "PY_LINECOUNT",
      category = "PYTHON_METRICS",
      severity = RuleSeverity.Info,
      passed = true,
      message = s"Dein Python-Code enthält $nonEmptyLineCount nicht-leere Zeile(n).",
      details = Some(nonEmptyLineCount.toString)
    )

    // Long lines
    val longLines = lines.zipWithIndex.filter { case (line, _) => line.length > MaxRecommendedLineLength }
    if longLines.nonEmpty then {
      val count = longLines.size
      val linesStr = longLines.map { case (_, idx) => idx + 1 }.mkString("Zeilen: ", ", ", "")
      results += RuleResult(
        id = "PY_LONG_LINES",
        category = "PYTHON_STYLE",
        severity = RuleSeverity.Warning,
        passed = false,
        message = s"$count Zeile(n) sind länger als $MaxRecommendedLineLength Zeichen. Kürzere Zeilen verbessern die Lesbarkeit.",
        details = Some(linesStr)
      )
    } else {
      results += RuleResult(
        id = "PY_LONG_LINES",
        category = "PYTHON_STYLE",
        severity = RuleSeverity.Info,
        passed = true,
        message = s"Keine Zeile überschreitet die empfohlene maximale Länge von $MaxRecommendedLineLength Zeichen.",
        details = None
      )
    }

    // Trailing whitespace
    val trailing = lines.zipWithIndex.filter { case (line, _) =>
      line.nonEmpty && (line.endsWith(" ") || line.endsWith("\t"))
    }
    if trailing.nonEmpty then {
      val linesStr = trailing.map { case (_, idx) => idx + 1 }.mkString("Zeilen: ", ", ", "")
      results += RuleResult(
        id = "PY_TRAILING_WHITESPACE",
        category = "PYTHON_STYLE",
        severity = RuleSeverity.Info,
        passed = false,
        message = "Einige Zeilen enthalten Leerzeichen am Zeilenende. Diese kannst du normalerweise entfernen.",
        details = Some(linesStr)
      )
    } else {
      results += RuleResult(
        id = "PY_TRAILING_WHITESPACE",
        category = "PYTHON_STYLE",
        severity = RuleSeverity.Info,
        passed = true,
        message = "Es wurden keine unnötigen Leerzeichen am Zeilenende gefunden.",
        details = None
      )
    }

    // Consecutive blank lines
    val maxBlankStreak = computeMaxBlankStreak(lines)
    if maxBlankStreak > MaxRecommendedBlankStreak then {
      results += RuleResult(
        id = "PY_BLANK_LINES",
        category = "PYTHON_STYLE",
        severity = RuleSeverity.Info,
        passed = false,
        message = s"Es gibt Blöcke mit mehr als $MaxRecommendedBlankStreak aufeinanderfolgenden Leerzeilen. Versuche, leere Zeilen sparsamer einzusetzen.",
        details = Some(s"Maximaler Block leerer Zeilen: $maxBlankStreak")
      )
    } else {
      results += RuleResult(
        id = "PY_BLANK_LINES",
        category = "PYTHON_STYLE",
        severity = RuleSeverity.Info,
        passed = true,
        message = "Leerzeilen werden moderat eingesetzt.",
        details = None
      )
    }

    // TODO / pass -> incomplete code
    val lower   = normalized.toLowerCase
    val hasTodo = lower.contains("todo")
    val hasPass = """\bpass\b""".r.findFirstIn(lower).nonEmpty

    if hasTodo || hasPass then {
      results += RuleResult(
        id = "PY_INCOMPLETE_CODE",
        category = "PYTHON_SEMANTICS",
        severity = RuleSeverity.Warning,
        passed = false,
        message = "Der Code enthält Platzhalter wie 'pass' oder 'TODO'-Kommentare. Vermutlich ist die Lösung noch nicht vollständig.",
        details = None
      )
    } else {
      results += RuleResult(
        id = "PY_INCOMPLETE_CODE",
        category = "PYTHON_SEMANTICS",
        severity = RuleSeverity.Info,
        passed = true,
        message = "Es wurden keine offensichtlichen Platzhalter wie 'pass' oder 'TODO' gefunden.",
        details = None
      )
    }

    // print spam
    val printRegex: Regex = """\bprint\s*\(""".r
    val printCount        = printRegex.findAllMatchIn(normalized).length

    if printCount > MaxRecommendedPrintStatements then {
      results += RuleResult(
        id = "PY_PRINT_SPAM",
        category = "PYTHON_STYLE",
        severity = RuleSeverity.Info,
        passed = false,
        message = s"Es wurden $printCount 'print'-Anweisungen gefunden. Nutze Ausgaben sparsam oder entferne Debug-Ausgaben.",
        details = Some(printCount.toString)
      )
    } else {
      results += RuleResult(
        id = "PY_PRINT_SPAM",
        category = "PYTHON_STYLE",
        severity = RuleSeverity.Info,
        passed = true,
        message = s"Die Anzahl von 'print'-Ausgaben ($printCount) ist moderat.",
        details = Some(printCount.toString)
      )
    }

    results.toList
  }

  private def computeMaxBlankStreak(lines: IndexedSeq[String]): Int = {
    var current = 0
    var max     = 0

    lines.foreach { line =>
      if line.trim.isEmpty then
        current += 1
      else {
        if current > max then max = current
        current = 0
      }
    }

    if current > max then max = current
    max
  }
}
