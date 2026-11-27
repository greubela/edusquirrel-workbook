package it.evadid.vm.analysis.feedback

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*

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

    if normalized.trim.isEmpty then List(emptyCodeRule(humanLanguage))
    else {
      val lines = normalized.split("\n", -1).toIndexedSeq
      List(
        nonEmptyRule(humanLanguage),
        lineCountRule(lines, humanLanguage),
        longLinesRule(lines, humanLanguage),
        trailingWhitespaceRule(lines, humanLanguage),
        blankLinesRule(lines, humanLanguage),
        incompleteCodeRule(humanLanguage),
        printSpamRule(normalized, humanLanguage)
      ) ++ PatternWarnings.flatMap(_.check(normalized, humanLanguage)) ++
        unusedImportsRule(lines, humanLanguage) ++
        missingReturnRule(lines, humanLanguage)
    }
  }

  private def passedRule(id: String, category: String, message: String, details: Option[String] = None): RuleResult =
    RuleResult(id = id, category = category, severity = RuleSeverity.Info, passed = true, message = message, details = details)

  private def failedRule(id: String, category: String, severity: RuleSeverity, message: String, details: Option[String]): RuleResult =
    RuleResult(id = id, category = category, severity = severity, passed = false, message = message, details = details)

  /** 1-based numbers of the selected lines, as the style rules report them. */
  private def lineNumbersDetail(selected: Seq[(String, Int)]): String =
    selected.map { case (_, idx) => idx + 1 }.mkString("Zeilen: ", ", ", "")

  private def emptyCodeRule(lang: HumanLanguage): RuleResult =
    RuleResult(
      id = "PY_EMPTY",
      category = "PYTHON_STRUCTURE",
      severity = RuleSeverity.Error,
      passed = false,
      message = t(lang)(
        "Es wurde kein Python-Code gefunden. Füge zunächst eine Lösung ein, bevor weitere Analysen sinnvoll sind.",
        "No Python code found. Add a solution first before further analysis makes sense."
      ),
      details = Some("Leerer oder nur aus Whitespace bestehender Code.")
    )

  private def nonEmptyRule(lang: HumanLanguage): RuleResult =
    passedRule("PY_NON_EMPTY", "PYTHON_STRUCTURE",
      t(lang)("Es liegt ausführbarer Python-Code vor.", "Executable Python code is present."))

  /** Counts only non-empty lines. */
  private def lineCountRule(lines: IndexedSeq[String], lang: HumanLanguage): RuleResult = {
    val nonEmptyLineCount = lines.count(_.trim.nonEmpty)
    passedRule("PY_LINECOUNT", "PYTHON_METRICS",
      t(lang)(
        s"Dein Python-Code enthält $nonEmptyLineCount nicht-leere Zeile(n).",
        s"Your Python code contains $nonEmptyLineCount non-empty line(s)."
      ),
      Some(nonEmptyLineCount.toString))
  }

  private def longLinesRule(lines: IndexedSeq[String], lang: HumanLanguage): RuleResult = {
    val longLines = lines.zipWithIndex.filter { case (line, _) => line.length > MaxRecommendedLineLength }
    if longLines.nonEmpty then {
      val count = longLines.size
      failedRule("PY_LONG_LINES", "PYTHON_STYLE", RuleSeverity.Warning,
        t(lang)(
          s"$count Zeile(n) sind länger als $MaxRecommendedLineLength Zeichen. Kürzere Zeilen verbessern die Lesbarkeit.",
          s"$count line(s) are longer than $MaxRecommendedLineLength characters. Shorter lines improve readability."
        ),
        Some(lineNumbersDetail(longLines)))
    } else
      passedRule("PY_LONG_LINES", "PYTHON_STYLE",
        t(lang)(
          s"Keine Zeile überschreitet die empfohlene maximale Länge von $MaxRecommendedLineLength Zeichen.",
          s"No line exceeds the recommended maximum length of $MaxRecommendedLineLength characters."
        ))
  }

  private def trailingWhitespaceRule(lines: IndexedSeq[String], lang: HumanLanguage): RuleResult = {
    val trailing = lines.zipWithIndex.filter { case (line, _) =>
      line.nonEmpty && (line.endsWith(" ") || line.endsWith("\t"))
    }
    if trailing.nonEmpty then
      failedRule("PY_TRAILING_WHITESPACE", "PYTHON_STYLE", RuleSeverity.Info,
        t(lang)(
          "Einige Zeilen enthalten Leerzeichen am Zeilenende. Diese kannst du normalerweise entfernen.",
          "Some lines contain trailing whitespace. You can usually remove it."
        ),
        Some(lineNumbersDetail(trailing)))
    else
      passedRule("PY_TRAILING_WHITESPACE", "PYTHON_STYLE",
        t(lang)(
          "Es wurden keine unnötigen Leerzeichen am Zeilenende gefunden.",
          "No trailing whitespace found."
        ))
  }

  /** Consecutive blank lines. */
  private def blankLinesRule(lines: IndexedSeq[String], lang: HumanLanguage): RuleResult = {
    val maxBlankStreak = computeMaxBlankStreak(lines)
    if maxBlankStreak > MaxRecommendedBlankStreak then
      failedRule("PY_BLANK_LINES", "PYTHON_STYLE", RuleSeverity.Info,
        t(lang)(
          s"Es gibt Blöcke mit mehr als $MaxRecommendedBlankStreak aufeinanderfolgenden Leerzeilen. Versuche, leere Zeilen sparsamer einzusetzen.",
          s"There are blocks with more than $MaxRecommendedBlankStreak consecutive blank lines. Try to use blank lines more sparingly."
        ),
        Some(s"Maximaler Block leerer Zeilen: $maxBlankStreak"))
    else
      passedRule("PY_BLANK_LINES", "PYTHON_STYLE",
        t(lang)("Leerzeilen werden moderat eingesetzt.", "Blank lines are used moderately."))
  }

  private def incompleteCodeRule(lang: HumanLanguage): RuleResult =
    passedRule("PY_INCOMPLETE_CODE", "PYTHON_SEMANTICS",
      t(lang)(
        "Es wurden keine offensichtlichen Platzhalter gefunden.",
        "No obvious placeholders found."
      ))

  private val PrintPattern: Regex = """\bprint\s*\(""".r

  private def printSpamRule(code: String, lang: HumanLanguage): RuleResult = {
    val printCount = PrintPattern.findAllMatchIn(code).length
    if printCount > MaxRecommendedPrintStatements then
      failedRule("PY_PRINT_SPAM", "PYTHON_STYLE", RuleSeverity.Warning,
        t(lang)(
          s"Es wurden $printCount 'print'-Anweisungen gefunden. Entferne Debug-Ausgaben oder reduziere die Anzahl deutlich.",
          s"Found $printCount print statements. Remove debug output or reduce it significantly."
        ),
        Some(printCount.toString))
    else
      passedRule("PY_PRINT_SPAM", "PYTHON_STYLE",
        t(lang)(
          s"Die Anzahl von 'print'-Ausgaben ($printCount) ist moderat.",
          s"The number of print statements ($printCount) is moderate."
        ),
        Some(printCount.toString))
  }

  /** A semantic warning reported only when its pattern occurs in the code. */
  private final case class PatternWarning(id: String, pattern: Regex, de: String, en: String) {
    def check(code: String, lang: HumanLanguage): Option[RuleResult] =
      Option.when(pattern.findFirstIn(code).nonEmpty)(
        failedRule(id, "PYTHON_SEMANTICS", RuleSeverity.Warning, t(lang)(de, en), None))
  }

  private val PatternWarnings: List[PatternWarning] = List(
    // input() is non-deterministic and blocks in many graders.
    PatternWarning("PY_INPUT_USAGE", """\binput\s*\(""".r,
      "Dein Code nutzt input(). In automatischen Tests gibt es oft keine interaktive Eingabe; nutze stattdessen Funktionsparameter.",
      "Your code uses input(). In automated tests there is often no interactive input; use function parameters instead."),
    // A while True loop is often infinite.
    PatternWarning("PY_WHILE_TRUE", """\bwhile\s+True\b""".r,
      "Es wurde 'while True' gefunden. Prüfe, ob die Schleife sicher beendet wird (sonst droht Timeout).",
      "Found 'while True'. Make sure the loop terminates safely (otherwise you may hit a timeout)."),
    // time.sleep is slow and may cause timeouts.
    PatternWarning("PY_SLEEP_USAGE", """\btime\s*\.\s*sleep\s*\(""".r,
      "time.sleep() verlangsamt Tests und kann zu Timeouts führen. Entferne Sleeps in Abgaben.",
      "time.sleep() slows down tests and may cause timeouts. Remove sleeps from submissions."),
    PatternWarning("PY_RANDOM_USAGE", """\brandom\s*\.""".r,
      "Zufall (random) kann Tests nicht-deterministisch machen. Falls nötig, setze einen festen Seed oder vermeide Randomness.",
      "Randomness (random) can make tests non-deterministic. If needed, set a fixed seed or avoid randomness.")
  )

  /** Reported only when the code imports something. */
  private def unusedImportsRule(lines: IndexedSeq[String], lang: HumanLanguage): Option[RuleResult] = {
    val importLines = lines.zipWithIndex.filter { case (line, _) =>
      val tr = line.trim
      (tr.startsWith("import ") || tr.startsWith("from ")) && !tr.startsWith("#")
    }
    val importLineNumbers = importLines.map(_._2).toSet
    val codeWithoutImports = lines.zipWithIndex.collect { case (line, idx) if !importLineNumbers(idx) => line }.mkString("\n")
    val unusedImports = importLines.flatMap { case (line, lineNum) =>
      importedNames(line.trim)
        .filterNot(name => s"\\b${Regex.quote(name)}\\b".r.findFirstIn(codeWithoutImports).nonEmpty)
        .map(name => (lineNum + 1, name))
    }
    if unusedImports.nonEmpty then {
      val detail = unusedImports.map { case (ln, m) => s"line $ln: '$m'" }.mkString(", ")
      Some(failedRule("PY_UNUSED_IMPORTS", "PYTHON_STYLE", RuleSeverity.Warning,
        t(lang)(
          s"${unusedImports.size} nicht verwendete(r) Import(s): $detail.",
          s"${unusedImports.size} unused import(s): $detail."
        ),
        Some(detail)))
    } else Option.when(importLines.nonEmpty)(
      passedRule("PY_UNUSED_IMPORTS", "PYTHON_STYLE", t(lang)("Alle Importe werden verwendet.", "All imports are used.")))
  }

  private val DefPattern = """^(?:async\s+)?def\s+([a-zA-Z_]\w*)\s*\(""".r
  private val ReturnPattern = """\breturn\b""".r

  /** Reported only when the code defines top-level functions; stub bodies need no `return`. */
  private def missingReturnRule(lines: IndexedSeq[String], lang: HumanLanguage): Option[RuleResult] = {
    val defLineIndices = lines.zipWithIndex.collect {
      case (line, idx) if line.startsWith("def ") || line.startsWith("async def ") => idx
    }
    Option.when(defLineIndices.nonEmpty) {
      val missingReturn = defLineIndices.flatMap(start => functionWithoutReturn(lines, start))
      if missingReturn.nonEmpty then {
        val detail = missingReturn.mkString(", ")
        failedRule("PY_MISSING_RETURN", "PYTHON_SEMANTICS", RuleSeverity.Warning,
          t(lang)(
            s"Folgende Funktion(en) fehlt ein 'return': $detail.",
            s"The following function(s) appear to have no 'return': $detail."
          ),
          Some(detail))
      } else
        passedRule("PY_MISSING_RETURN", "PYTHON_SEMANTICS",
          t(lang)(
            "Alle Funktionen enthalten mindestens ein 'return'.",
            "All functions contain at least one 'return' statement."
          ))
    }
  }

  /** The function defined at line `start`, described for the report, when its body has no `return` and is not a stub. */
  private def functionWithoutReturn(lines: IndexedSeq[String], start: Int): Option[String] = {
    // The body ends at the next unindented code line.
    val bodyLines = lines.drop(start + 1).takeWhile(l =>
      l.trim.isEmpty || l.head.isWhitespace || l.trim.startsWith("#")
    )
    val hasReturn = bodyLines.exists(l => ReturnPattern.findFirstIn(l.takeWhile(_ != '#')).nonEmpty)
    val isStub = bodyLines.forall(l =>
      l.trim.isEmpty || l.trim == "pass" || l.trim == "..." || l.trim.startsWith("#")
    )
    Option.when(!hasReturn && !isStub) {
      val name = DefPattern.findFirstMatchIn(lines(start).trim).map(_.group(1)).getOrElse("?")
      s"'$name' (line ${start + 1})"
    }
  }

  /** Names an import statement binds: aliases, top-level packages of plain imports, nothing for `*`. */
  private def importedNames(statement: String): Seq[String] = {
    val code = statement.takeWhile(_ != '#').trim
    def boundName(clause: String, plainImport: Boolean): Option[String] =
      clause.trim.split("\\s+").toList match {
        case _ :: "as" :: alias :: Nil => Some(alias)
        case "*" :: Nil => None
        case name :: Nil if name.nonEmpty => Some(if plainImport then name.takeWhile(_ != '.') else name)
        case _ => None
      }
    def clauses(text: String): Seq[String] =
      text.filterNot(c => c == '(' || c == ')').split(",").toSeq.map(_.trim).filter(_.nonEmpty)

    if code.startsWith("import ") then clauses(code.drop(7)).flatMap(boundName(_, plainImport = true))
    else code.indexOf(" import ") match {
      case -1 => Nil
      case idx => clauses(code.drop(idx + 8)).flatMap(boundName(_, plainImport = false))
    }
  }

  private def computeMaxBlankStreak(lines: IndexedSeq[String]): Int = {
    val (longest, current) = lines.foldLeft((0, 0)) { case ((max, streak), line) =>
      if line.trim.isEmpty then (max, streak + 1) else (math.max(max, streak), 0)
    }
    math.max(longest, current)
  }
}
