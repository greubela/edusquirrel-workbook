package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.rules

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*

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
  private val MaxRecommendedPrintStatements = 3

  private def t(lang: HumanLanguage)(de: String, en: String): String =
    if lang == AppLanguage.German then de else en

  def runAll(rawCode: String, humanLanguage: HumanLanguage = AppLanguage.default()): Seq[RuleResult] = {
    val normalized = Option(rawCode).getOrElse("").replace("\r\n", "\n")
    val trimmed    = normalized.trim

    val results = mutable.ListBuffer.empty[RuleResult]

    if trimmed.isEmpty then {
      results += RuleResult(
        id = "PY_EMPTY",
        category = "PYTHON_STRUCTURE",
        severity = RuleSeverity.Error,
        passed = false,
        message = t(humanLanguage)(
          "Es wurde kein Python-Code gefunden. Füge zunächst eine Lösung ein, bevor weitere Analysen sinnvoll sind.",
          "No Python code found. Add a solution first before further analysis makes sense."
        ),
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
      message = t(humanLanguage)("Es liegt ausführbarer Python-Code vor.", "Executable Python code is present."),
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
      message = t(humanLanguage)(
        s"Dein Python-Code enthält $nonEmptyLineCount nicht-leere Zeile(n).",
        s"Your Python code contains $nonEmptyLineCount non-empty line(s)."
      ),
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
        message = t(humanLanguage)(
          s"$count Zeile(n) sind länger als $MaxRecommendedLineLength Zeichen. Kürzere Zeilen verbessern die Lesbarkeit.",
          s"$count line(s) are longer than $MaxRecommendedLineLength characters. Shorter lines improve readability."
        ),
        details = Some(linesStr)
      )
    } else {
      results += RuleResult(
        id = "PY_LONG_LINES",
        category = "PYTHON_STYLE",
        severity = RuleSeverity.Info,
        passed = true,
        message = t(humanLanguage)(
          s"Keine Zeile überschreitet die empfohlene maximale Länge von $MaxRecommendedLineLength Zeichen.",
          s"No line exceeds the recommended maximum length of $MaxRecommendedLineLength characters."
        ),
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
        message = t(humanLanguage)(
          "Einige Zeilen enthalten Leerzeichen am Zeilenende. Diese kannst du normalerweise entfernen.",
          "Some lines contain trailing whitespace. You can usually remove it."
        ),
        details = Some(linesStr)
      )
    } else {
      results += RuleResult(
        id = "PY_TRAILING_WHITESPACE",
        category = "PYTHON_STYLE",
        severity = RuleSeverity.Info,
        passed = true,
        message = t(humanLanguage)(
          "Es wurden keine unnötigen Leerzeichen am Zeilenende gefunden.",
          "No trailing whitespace found."
        ),
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
        message = t(humanLanguage)(
          s"Es gibt Blöcke mit mehr als $MaxRecommendedBlankStreak aufeinanderfolgenden Leerzeilen. Versuche, leere Zeilen sparsamer einzusetzen.",
          s"There are blocks with more than $MaxRecommendedBlankStreak consecutive blank lines. Try to use blank lines more sparingly."
        ),
        details = Some(s"Maximaler Block leerer Zeilen: $maxBlankStreak")
      )
    } else {
      results += RuleResult(
        id = "PY_BLANK_LINES",
        category = "PYTHON_STYLE",
        severity = RuleSeverity.Info,
        passed = true,
        message = t(humanLanguage)("Leerzeilen werden moderat eingesetzt.", "Blank lines are used moderately."),
        details = None
      )
    }

    results += RuleResult(
      id = "PY_INCOMPLETE_CODE",
      category = "PYTHON_SEMANTICS",
      severity = RuleSeverity.Info,
      passed = true,
      message = t(humanLanguage)(
        "Es wurden keine offensichtlichen Platzhalter gefunden.",
        "No obvious placeholders found."
      ),
      details = None
    )

    // print spam
    val printRegex: Regex = """\bprint\s*\(""".r
    val printCount        = printRegex.findAllMatchIn(normalized).length

    if printCount > MaxRecommendedPrintStatements then {
      results += RuleResult(
        id = "PY_PRINT_SPAM",
        category = "PYTHON_STYLE",
        severity = RuleSeverity.Warning,
        passed = false,
        message = t(humanLanguage)(
          s"Es wurden $printCount 'print'-Anweisungen gefunden. Entferne Debug-Ausgaben oder reduziere die Anzahl deutlich.",
          s"Found $printCount print statements. Remove debug output or reduce it significantly."
        ),
        details = Some(printCount.toString)
      )
    } else {
      results += RuleResult(
        id = "PY_PRINT_SPAM",
        category = "PYTHON_STYLE",
        severity = RuleSeverity.Info,
        passed = true,
        message = t(humanLanguage)(
          s"Die Anzahl von 'print'-Ausgaben ($printCount) ist moderat.",
          s"The number of print statements ($printCount) is moderate."
        ),
        details = Some(printCount.toString)
      )
    }

    // input() usage (non-deterministic / blocks in many graders)
    val inputRegex: Regex = """\binput\s*\(""".r
    if inputRegex.findFirstIn(normalized).nonEmpty then {
      results += RuleResult(
        id = "PY_INPUT_USAGE",
        category = "PYTHON_SEMANTICS",
        severity = RuleSeverity.Warning,
        passed = false,
        message = t(humanLanguage)(
          "Dein Code nutzt input(). In automatischen Tests gibt es oft keine interaktive Eingabe; nutze stattdessen Funktionsparameter.",
          "Your code uses input(). In automated tests there is often no interactive input; use function parameters instead."
        ),
        details = None
      )
    }

    // while True loop (often infinite)
    val whileTrueRegex: Regex = """\bwhile\s+True\b""".r
    if whileTrueRegex.findFirstIn(normalized).nonEmpty then {
      results += RuleResult(
        id = "PY_WHILE_TRUE",
        category = "PYTHON_SEMANTICS",
        severity = RuleSeverity.Warning,
        passed = false,
        message = t(humanLanguage)(
          "Es wurde 'while True' gefunden. Prüfe, ob die Schleife sicher beendet wird (sonst droht Timeout).",
          "Found 'while True'. Make sure the loop terminates safely (otherwise you may hit a timeout)."
        ),
        details = None
      )
    }

    // time.sleep usage (slow / may cause timeout)
    val sleepRegex: Regex = """\btime\s*\.\s*sleep\s*\(""".r
    if sleepRegex.findFirstIn(normalized).nonEmpty then {
      results += RuleResult(
        id = "PY_SLEEP_USAGE",
        category = "PYTHON_SEMANTICS",
        severity = RuleSeverity.Warning,
        passed = false,
        message = t(humanLanguage)(
          "time.sleep() verlangsamt Tests und kann zu Timeouts führen. Entferne Sleeps in Abgaben.",
          "time.sleep() slows down tests and may cause timeouts. Remove sleeps from submissions."
        ),
        details = None
      )
    }

    val randomUsageRegex: Regex = """\brandom\s*\.""".r
    if randomUsageRegex.findFirstIn(normalized).nonEmpty then {
      results += RuleResult(
        id = "PY_RANDOM_USAGE",
        category = "PYTHON_SEMANTICS",
        severity = RuleSeverity.Warning,
        passed = false,
        message = t(humanLanguage)(
          "Zufall (random) kann Tests nicht-deterministisch machen. Falls nötig, setze einen festen Seed oder vermeide Randomness.",
          "Randomness (random) can make tests non-deterministic. If needed, set a fixed seed or avoid randomness."
        ),
        details = None
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
