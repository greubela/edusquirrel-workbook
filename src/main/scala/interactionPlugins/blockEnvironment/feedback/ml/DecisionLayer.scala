package interactionPlugins.blockEnvironment.feedback.ml

/**
 * MVP Mini-ML v1.1: deterministic routing layer with the same output API
 * that a future ML model will expose.
 */
object DecisionLayer {

  enum IssueType {
    case CORRECT
    case COMPILE_ERROR
    case API_SIGNATURE
    case PERFORMANCE
    case FORMAT_OUTPUT
    case IO_CONTRACT
    case INCOMPLETE_IMPLEMENTATION
    case BOUNDARY_CONDITION
    case EXCEPTION_TYPE
    case NONDETERMINISM
    case LOGIC_EDGE_CASE
  }

  enum Severity {
    case LOW
    case MEDIUM
    case HIGH
  }

  final case class Evidence(key: String, value: String)

  final case class Decision(
    primaryIssue: IssueType,
    secondaryIssues: Seq[IssueType],
    severity: Severity,
    confidence: Double,
    topCauses: Seq[String],
    evidence: Seq[Evidence],
    /**
     * True when the ML router predicted "CORRECT" with sufficient confidence AND
     * the heuristic fallback was only a soft catch-all (LOGIC_EDGE_CASE).
     *
     * Consumers (e.g. BlockFeedbackService) should use this to suppress LLM
     * calls and other non-essential processing when the code is likely correct.
     * It is never set by the heuristic router itself only by MlRouter.
     */
    mlCorrectSignal: Boolean = false
  )

  private def mkDecision(
    primaryIssue: IssueType,
    confidence: Double,
    topCauses: Seq[String],
    severity: Severity = Severity.MEDIUM,
    secondaryIssues: Seq[IssueType] = Nil,
    evidence: Seq[Evidence] = Nil
  ): Decision =
    Decision(
      primaryIssue = primaryIssue,
      secondaryIssues = secondaryIssues.distinct.take(2),
      severity = severity,
      confidence = confidence,
      topCauses = topCauses,
      evidence = evidence
    )

  def heuristicRoute(signals: BlockFeedbackSignals): Decision = {
    val runtimeError = signals.runtimeOutcome.runtimeError.getOrElse("")
    val stderr = signals.runtimeOutcome.stderr.getOrElse("")
    val testErrorText = signals.runtimeOutcome.tests
      .flatMap(t => Seq(t.actual, t.expected) ++ t.message.toSeq)
      .mkString("\n")
    val combinedError = s"$runtimeError\n$stderr\n$testErrorText".toLowerCase

    val hasRuntimeError = signals.runtimeOutcome.runtimeError.exists(_.trim.nonEmpty)
    val allTestsPassed = signals.runtimeOutcome.tests.nonEmpty &&
      signals.runtimeOutcome.tests.forall(_.passed)

    if allTestsPassed && !hasRuntimeError then {
      return mkDecision(
        primaryIssue = IssueType.CORRECT,
        confidence = 1.0,
        topCauses = Seq("all tests passed"),
        severity = Severity.LOW
      )
    }

    // compile errors
    if combinedError.contains("syntaxerror") || combinedError.contains("indentationerror") then {
      val causes = Seq("runtimeError contains SyntaxError/IndentationError").filter(_.nonEmpty)
      return mkDecision(
        primaryIssue = IssueType.COMPILE_ERROR,
        confidence = 0.95,
        topCauses = causes,
        severity = Severity.HIGH,
        evidence = Seq(Evidence("runtimeErrorType", "SyntaxError/IndentationError"))
      )
    }

    // NameError + "did you mean" = function name typo; check before the generic exception block
    if combinedError.contains("nameerror") && combinedError.contains("did you mean") then {
      return mkDecision(
        primaryIssue = IssueType.API_SIGNATURE,
        confidence = 0.92,
        topCauses = Seq("NameError with 'did you mean' suggests function name typo"),
        severity = Severity.MEDIUM,
        evidence = Seq(Evidence("exception", "nameerror"), Evidence("hint", "did you mean"))
      )
    }

    // runtime exceptions
    val exceptionTypes = Seq(
      "typeerror",
      "indexerror",
      "keyerror",
      "valueerror",
      "zerodivisionerror",
      "attributeerror",
      "nameerror",
      "modulenotfounderror",
      "importerror"
    )
    exceptionTypes.find(combinedError.contains) match {
      case Some(exc) =>
        val causes = Seq(s"runtimeError contains ${exc.capitalize}")
        return mkDecision(
          primaryIssue = IssueType.EXCEPTION_TYPE,
          confidence = 0.88,
          topCauses = causes,
          severity = Severity.HIGH,
          evidence = Seq(Evidence("exception", exc))
        )
      case None =>
    }

    // timeout
    if combinedError.contains("timed out") || combinedError.contains("timeout") then {
      return mkDecision(
        primaryIssue = IssueType.PERFORMANCE,
        confidence = 0.85,
        topCauses = Seq("runtime timeout"),
        severity = Severity.HIGH,
        evidence = Seq(Evidence("runtime", "timeout"))
      )
    }

    val testTextsLower: Seq[String] =
      signals.runtimeOutcome.tests.flatMap(t => Seq(t.name, t.expected, t.actual) ++ t.message.toSeq).map(_.toLowerCase)

    val mentionsFunctionMissing =
      testTextsLower.exists(s => s.contains("function missing") || s.contains("missing function") || s.contains("missing") && s.contains("function"))

    val mentionsNameOrAttrError = combinedError.contains("nameerror") || combinedError.contains("attributeerror")

    // API signature: NameError + test mentions missing function
    if mentionsNameOrAttrError && mentionsFunctionMissing then {
      val causes = Seq("NameError/AttributeError", "tests indicate function missing")
      return mkDecision(
        primaryIssue = IssueType.API_SIGNATURE,
        confidence = 0.8,
        topCauses = causes,
        severity = Severity.MEDIUM,
        evidence = Seq(Evidence("functionMissing", "true"))
      )
    }

    val testsTotal = signals.runtimeOutcome.tests.size
    val testsFailed = signals.runtimeOutcome.tests.count(!_.passed)
    val hasFailingTests = testsTotal > 0 && testsFailed > 0

    val ioSignal = signals.inputCallCount >= 1

    // input() usage with failing tests = I/O contract issue
    if hasFailingTests && ioSignal then {
      return mkDecision(
        primaryIssue = IssueType.IO_CONTRACT,
        confidence = 0.85,
        topCauses = Seq("input() usage detected"),
        severity = Severity.HIGH,
        evidence = Seq(Evidence("inputCallCount", signals.inputCallCount.toString))
      )
    }

    val manyFails = testsTotal >= 3 && testsFailed.toDouble / testsTotal.toDouble >= 0.6

    val minPrintsForFormat = 2
    val stdoutSignal = signals.stdoutLineCount >= 1
    val printSignal = signals.printCount >= minPrintsForFormat
    val stdoutSuspicious = stdoutSignal && printSignal

    if manyFails && stdoutSuspicious then {
      val causes = Seq(
        s"many test failures ($testsFailed/$testsTotal)",
        s"stdout present (${signals.stdoutLineCount} line(s))",
        s"print() usage high (${signals.printCount})"
      )
      return mkDecision(
        primaryIssue = IssueType.FORMAT_OUTPUT,
        confidence = 0.8,
        topCauses = causes,
        severity = Severity.MEDIUM,
        secondaryIssues = if signals.printCount > 0 then Seq(IssueType.IO_CONTRACT) else Nil,
        evidence = Seq(
          Evidence("stdoutLineCount", signals.stdoutLineCount.toString),
          Evidence("printCount", signals.printCount.toString)
        )
      )
    }

    // pass statement with failing tests = incomplete implementation (skip if there's a runtime error)
    if hasFailingTests && signals.hasPassStatement && !hasRuntimeError then {
      return mkDecision(
        primaryIssue = IssueType.INCOMPLETE_IMPLEMENTATION,
        confidence = 0.82,
        topCauses = Seq("pass statement found in student code"),
        severity = Severity.MEDIUM,
        evidence = Seq(Evidence("hasPassStatement", "true"))
      )
    }

    // random() usage is a more specific, identifiable signal than a generic boundary hint —
    // check it before BOUNDARY_CONDITION so it is not silently subsumed.
    if hasFailingTests && signals.randomCallCount > 0 then {
      return mkDecision(
        primaryIssue = IssueType.NONDETERMINISM,
        confidence = 0.76,
        topCauses = Seq("randomness usage detected"),
        severity = Severity.MEDIUM,
        evidence = Seq(Evidence("randomCallCount", signals.randomCallCount.toString))
      )
    }

    val boundarySignal = signals.boundaryHintScore >= 2 && testsTotal >= 2
    if hasFailingTests && boundarySignal then {
      return mkDecision(
        primaryIssue = IssueType.BOUNDARY_CONDITION,
        confidence = 0.78,
        topCauses = Seq("failing tests suggest boundary/edge-case"),
        severity = Severity.MEDIUM,
        evidence = Seq(Evidence("boundaryHintScore", signals.boundaryHintScore.toString))
      )
    }

    val baseConfidence = if hasFailingTests then 0.75 else 0.6
    mkDecision(
      primaryIssue = IssueType.LOGIC_EDGE_CASE,
      confidence = baseConfidence,
      topCauses = Seq("default"),
      severity = if hasFailingTests then Severity.MEDIUM else Severity.LOW
    )
  }

  def route(signals: BlockFeedbackSignals, mode: RouterMode = RouterMode.Heuristic): Decision =
    mode match
      case RouterMode.Heuristic => heuristicRoute(signals)
      case RouterMode.Ml =>
        val weak = heuristicRoute(signals)
        MlRouter.routeOrFallback(signals, weak)

  def templateIdFor(issueType: IssueType): String = issueType match {
    case IssueType.CORRECT         => "T_CORRECT"
    case IssueType.COMPILE_ERROR   => "T_COMPILE_ERROR"
    case IssueType.API_SIGNATURE   => "T_API_SIGNATURE"
    case IssueType.PERFORMANCE     => "T_PERFORMANCE"
    case IssueType.FORMAT_OUTPUT   => "T_FORMAT_OUTPUT"
    case IssueType.IO_CONTRACT     => "T_IO_CONTRACT"
    case IssueType.INCOMPLETE_IMPLEMENTATION => "T_INCOMPLETE_IMPLEMENTATION"
    case IssueType.BOUNDARY_CONDITION => "T_BOUNDARY_CONDITION"
    case IssueType.EXCEPTION_TYPE  => "T_EXCEPTION_TYPE"
    case IssueType.NONDETERMINISM  => "T_NONDETERMINISM"
    case IssueType.LOGIC_EDGE_CASE => "T_LOGIC_EDGE_CASE"
  }
}
