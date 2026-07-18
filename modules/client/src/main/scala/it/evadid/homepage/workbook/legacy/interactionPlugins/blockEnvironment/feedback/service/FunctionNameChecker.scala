package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.service

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.model.PythonTestResult
/**
 * Detects function-name mismatches: the student named their function differently
 * from what the test suite expects.
 *
 * All operations are synchronous string analysis — zero extra latency.
 */
object FunctionNameChecker:

  // NameError: name 'foo' is not defined
  private val nameErrorRe = """NameError: name '(\w+)' is not defined""".r

  // matches a def on a single line (no (?m) flag — not supported in Scala.js ES5 target)
  private val defLineRe = raw"\s*def\s+([a-zA-Z_]\w*)\s*\(".r

  // any lowercase identifier immediately followed by ( — likely a function call
  private val callRe = raw"([a-z_][a-z0-9_]*)\s*\(".r

  // Python builtins and common test helpers that are never student functions
  private val builtins: Set[String] = Set(
    "assert", "print", "len", "range", "list", "dict", "set", "tuple",
    "int", "str", "float", "bool", "isinstance", "type", "sorted",
    "reversed", "zip", "map", "filter", "enumerate", "sum", "min",
    "max", "abs", "round", "input", "open", "hasattr", "getattr",
    "setattr", "repr", "id", "callable", "vars", "dir", "super",
    "object", "format", "all", "any", "next", "iter", "hash",
    "exception", "valueerror", "typeerror", "indexerror", "keyerror",
    "attributeerror", "runtimeerror", "stopiteration", "notimplementederror"
  )

  /** Function names defined in the student's source (`def foo(...)`). */
  def definedNames(source: String): Set[String] =
    source.linesIterator
      .flatMap(line => defLineRe.findFirstMatchIn(line).map(_.group(1)))
      .toSet

  /** Function names called inside the test suite (minus Python builtins).
   *  Single-character names are excluded: they are almost always characters
   */
  def expectedFromTestCode(testPlan: BlockFeedbackTestPlan): Set[String] =
    val allCode = (testPlan.visibleTests ++ testPlan.hiddenTests).map(_.code).mkString("\n")
    callRe.findAllMatchIn(allCode).map(_.group(1)).filter(_.length >= 2).toSet -- builtins

  /** Function names that appear in `NameError: name 'X' is not defined` messages. */
  private def confirmedNameErrors(tests: Seq[PythonTestResult]): Set[String] =
    tests
      .flatMap(t => t.message.toSeq ++ Seq(t.actual))
      .flatMap(msg => nameErrorRe.findAllMatchIn(msg).map(_.group(1)))
      .toSet

  /** Returns true when the two names are similar enough to be a rename candidate. */
  private def isSimilar(a: String, b: String): Boolean =
    val aN = a.toLowerCase.replace("_", "")
    val bN = b.toLowerCase.replace("_", "")
    aN == bN || aN.contains(bN) || bN.contains(aN)

  /**
   * @param missingFunctions  expected names absent from the student's code
   * @param definedFunctions  all names the student actually defined
   * @param suggestions       for each missing name, the closest defined name (rename candidates)
   */
  final case class NameCheckResult(
    missingFunctions: Set[String],
    definedFunctions: Set[String],
    suggestions: Map[String, String]
  ):
    /** True when we have at least one clear rename candidate to tell the student about. */
    def hasMismatch: Boolean = suggestions.nonEmpty

    /** Human-readable hint explaining the naming issue, or None when no mismatch. */
    def hintOption(language: HumanLanguage): Option[String] =
      if !hasMismatch then None else Some(buildHint(language))

    private def buildHint(language: HumanLanguage): String =
      val isGerman = language == AppLanguage.German
      val lines = missingFunctions.toSeq.sorted.flatMap { missing =>
        suggestions.get(missing).map { actual =>
          if isGerman then
            s"Deine Funktion hei\u00dft \u201e$actual\u201c, aber laut Aufgabenstellung soll die Funktion \u201e$missing\u201c hei\u00dfen. Bitte benenne sie um."
          else
            s"Your function is named \u201c$actual\u201d but the task description requires a function named \u201c$missing\u201d. Please rename it."
        }
      }
      lines.mkString("\n")

  /**
   * Runs both a static check (test-code calls vs. student defs) and a runtime
   * check (NameError messages from test results). The runtime signal, when
   * available, is preferred because it eliminates false positives.
   */
  def check(
    pythonSource: String,
    testPlan: BlockFeedbackTestPlan,
    runtimeTests: Seq[PythonTestResult]
  ): NameCheckResult =
    val defined      = definedNames(pythonSource)
    val fromTests    = expectedFromTestCode(testPlan)
    val nameErrors   = confirmedNameErrors(runtimeTests)

    // Runtime-confirmed names (NameErrors that also appear in the test code) are the
    // most reliable signal. Fall back to pure static analysis if no tests ran yet.
    val missing =
      if nameErrors.nonEmpty then
        (nameErrors intersect fromTests) -- defined
      else
        fromTests -- defined

    val suggestions: Map[String, String] =
      missing.flatMap { name =>
        defined.find(d => isSimilar(name, d)).map(name -> _)
      }.toMap

    NameCheckResult(
      missingFunctions = missing,
      definedFunctions = defined,
      suggestions = suggestions
    )
