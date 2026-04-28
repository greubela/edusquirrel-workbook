package interactionPlugins.blockEnvironment.feedback.ml

import interactionPlugins.blockEnvironment.feedback.{BlockFeedbackRequest, PythonRuntimeOutcome}
import interactionPlugins.blockEnvironment.feedback.rules.{RuleResult, RuleSeverity}

/**
 * Internal, centralized data container for downstream analytics/ML.
 *
 * This is intentionally NOT part of the UI feedback result; it's a clean
 * data pipeline object that can later be fed into a mini-ML model.
 */
final case class BlockFeedbackSignals(
  rawPython: String,
  pythonRules: Seq[RuleResult],
  vmRules: Seq[RuleResult],
  runtimeOutcome: PythonRuntimeOutcome,
  linesOfCode: Int,
  nonEmptyLineCount: Int,
  commentLineCount: Int,
  blankLineCount: Int,
  printCount: Int,
  inputCallCount: Int = 0,
  randomCallCount: Int = 0,
  hasPassStatement: Boolean = false,
  boundaryHintScore: Int = 0,
  stdoutLineCount: Int,
  stderrLineCount: Int,
  decision: Option[DecisionLayer.Decision] = None,
  templateId: Option[String] = None
) {

  def debugString: String =
    val testsTotal = runtimeOutcome.tests.size
    val testsPassed = runtimeOutcome.tests.count(_.passed)

    val pyFailedWarn = BlockFeedbackSignals.countFailed(pythonRules, RuleSeverity.Warning)
    val pyFailedErr  = BlockFeedbackSignals.countFailed(pythonRules, RuleSeverity.Error)
    val vmFailedWarn = BlockFeedbackSignals.countFailed(vmRules, RuleSeverity.Warning)
    val vmFailedErr  = BlockFeedbackSignals.countFailed(vmRules, RuleSeverity.Error)

    val statusStr = runtimeOutcome.runStatus.map(_.toString).getOrElse("<none>")
    val runtimeErrorStr = runtimeOutcome.runtimeError.getOrElse("")

    val decisionStr = decision
      .map(d => s"${d.primaryIssue} (conf=${d.confidence}, severity=${d.severity}, causes=${d.topCauses.mkString(";")})")
      .getOrElse("<none>")

    s"""BlockFeedbackSignals(
       |  rawPythonChars=${rawPython.length}
       |  linesOfCode=$linesOfCode
       |  nonEmptyLineCount=$nonEmptyLineCount
       |  commentLineCount=$commentLineCount
       |  blankLineCount=$blankLineCount
       |  printCount=$printCount
      |  inputCallCount=$inputCallCount
      |  randomCallCount=$randomCallCount
      |  hasPassStatement=$hasPassStatement
      |  boundaryHintScore=$boundaryHintScore
       |  stdoutLineCount=$stdoutLineCount
       |  stderrLineCount=$stderrLineCount
       |  runtimeStatus=$statusStr
       |  runtimeError=${if runtimeErrorStr.isEmpty then "<none>" else runtimeErrorStr}
       |  testsPassed=$testsPassed/$testsTotal
       |  pythonRulesFailed(warn/error)=$pyFailedWarn/$pyFailedErr
       |  vmRulesFailed(warn/error)=$vmFailedWarn/$vmFailedErr
      |  decision=$decisionStr
      |  templateId=${templateId.getOrElse("<none>")}
       |)""".stripMargin
}

object BlockFeedbackSignals {

  /** Flip to true locally when you want a single debug print in the pipeline. */
  private[ml] val DebugLoggingEnabled: Boolean = false

  def from(
    request: BlockFeedbackRequest,
    pythonRules: Seq[RuleResult],
    vmRules: Seq[RuleResult],
    runtimeOutcome: PythonRuntimeOutcome
  ): BlockFeedbackSignals =
    val raw = Option(request.pythonSource).getOrElse("")
    val normalized = raw.replace("\r\n", "\n")
    val lines = normalized.split("\n", -1).toIndexedSeq

    val nonEmpty = lines.count(_.trim.nonEmpty)
    val blank = lines.count(_.trim.isEmpty)
    val comment = lines.count(line => line.trim.startsWith("#"))

    val loc = lines.count { line =>
      val t = line.trim
      t.nonEmpty && !t.startsWith("#")
    }

    val printCount = countPrintStatements(normalized)
    val inputCallCount = countInputCalls(normalized)
    val randomCallCount = countRandomUsage(normalized)
    val hasPassStatement = containsPassStatement(normalized)
    val boundaryHintScore = computeBoundaryHintScore(runtimeOutcome)

    val stdoutLines = countOutputLines(runtimeOutcome.stdout.getOrElse(""))
    val stderrLines = countOutputLines(runtimeOutcome.stderr.getOrElse(""))

    BlockFeedbackSignals(
      rawPython = raw,
      pythonRules = pythonRules,
      vmRules = vmRules,
      runtimeOutcome = runtimeOutcome,
      linesOfCode = loc,
      nonEmptyLineCount = nonEmpty,
      commentLineCount = comment,
      blankLineCount = blank,
      printCount = printCount,
      inputCallCount = inputCallCount,
      randomCallCount = randomCallCount,
      hasPassStatement = hasPassStatement,
      boundaryHintScore = boundaryHintScore,
      stdoutLineCount = stdoutLines,
      stderrLineCount = stderrLines
    )

  private[feedback] def maybeDebugLog(signals: BlockFeedbackSignals): Unit =
    if DebugLoggingEnabled then println(signals.debugString)

  private def countOutputLines(output: String): Int =
    val normalized = Option(output).getOrElse("").replace("\r\n", "\n")
    if normalized.isEmpty then 0
    else normalized.split("\n", -1).count(_.nonEmpty)

  private def countPrintStatements(rawPython: String): Int =
    val normalized = Option(rawPython).getOrElse("").replace("\r\n", "\n")
    // intentionally simple (textual) heuristic
    val PrintCall = "\\bprint\\s*\\(".r
    PrintCall.findAllMatchIn(normalized).size

  private def countInputCalls(rawPython: String): Int =
    val normalized = Option(rawPython).getOrElse("").replace("\r\n", "\n")
    val InputCall = "\\binput\\s*\\(".r
    InputCall.findAllMatchIn(normalized).size

  private def countRandomUsage(rawPython: String): Int =
    val normalized = Option(rawPython).getOrElse("").replace("\r\n", "\n")
    val lower = normalized.toLowerCase
    // Keep it simple and Scala.js-friendly (no embedded (?i) flags)
    val RandomWord = "\\brandom\\b".r
    val ShuffleCall = "\\bshuffle\\s*\\(".r
    RandomWord.findAllMatchIn(lower).size + ShuffleCall.findAllMatchIn(lower).size

  private def containsPassStatement(rawPython: String): Boolean =
    val normalized = Option(rawPython).getOrElse("").replace("\r\n", "\n")
    // Scala.js RegExp does not support inline flags like (?m); do a simple line-based check.
    normalized
      .split("\n", -1)
      .iterator
      .map(_.trim)
      .exists(t => t == "pass" || t.startsWith("pass ") || t.startsWith("pass#"))

  private def computeBoundaryHintScore(runtimeOutcome: PythonRuntimeOutcome): Int =
    val failing = runtimeOutcome.tests.filterNot(_.passed)
    if failing.isEmpty then 0
    else {
      val texts = failing.flatMap(t => Seq(t.name, t.expected, t.actual) ++ t.message.toSeq).map(_.toLowerCase)
      val markers = Seq("empty", "single", "one", "edge", "boundary", "negative", "zero", "[]", "-", "0")
      markers.count(m => texts.exists(_.contains(m)))
    }

  private[ml] def countFailed(results: Seq[RuleResult], severity: RuleSeverity): Int =
    results.count(r => !r.passed && r.severity == severity)
}
