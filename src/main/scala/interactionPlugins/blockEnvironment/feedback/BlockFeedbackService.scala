package interactionPlugins.blockEnvironment.feedback

import contentmanagement.model.language.AppLanguage
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.parsing.python.PythonParser
import interactionPlugins.blockEnvironment.feedback.rules.{
  PythonStaticRules,
  RuleResult,
  RuleSeverity,
  VmStaticRules
}
import interactionPlugins.blockEnvironment.programming.BlockStudentCodeOrigin
import workbook.model.feedback.FeedbackStatus

import scala.util.Try

/**
 * Central orchestration of the block/Python feedback pipeline.
 *
 * Responsibilities:
 *  - derive canonical code representations (raw Python source + optional VM AST)
 *  - run static rules on VM and Python level
 *  - aggregate rule results into tests, hints, summary and score
 */
object BlockFeedbackService:

  def generateFeedback(request: BlockFeedbackRequest): BlockFeedbackResult =
    val (rawPython, vmForChecks) = deriveCodeRepresentations(request)

    val ruleResults = runStaticRules(request, vmForChecks, rawPython)

    val tests: Seq[PythonTestResult] =
      ruleResults.map(ruleToTestResult)

    val failedRules = ruleResults.filterNot(_.passed)

    val generalHints: Seq[String] =
      failedRules.map(_.message).distinct

    val (normalizedScore, status) =
      computeScoreAndStatus(rawPython, ruleResults)

    val summary = buildSummary(request.origin, ruleResults)

    UltrichsNewCoolFeedback(
      summary = summary,
      tests = tests,
      generalHints = generalHints,
      rawPython = rawPython,
      status = status,
      normalizedScore = normalizedScore
    )

  /**
   * Derive:
   *  - the Python source code string used for display & Python rules (rawPython)
   *  - an optional VM expression used for VM rules (vmForChecks)
   *
   * No pretty-printing happens here; we want to judge the real student code.
   */
  private def deriveCodeRepresentations(
      request: BlockFeedbackRequest
  ): (String, Option[BeExpression]) =
    request.studentCodePython match
      // Case 1: direct Python text provided by the student/editor
      case Some(studentCode) =>
        val original = Option(studentCode).getOrElse("")
        // Normalize line endings only; keep everything else as written
        val canonical =
          original.replace("\r\n", "\n").replace("\r", "\n")
        val trimmed = canonical.trim

        val vmFromRequest: Option[BeExpression] = request.vmExpression

        // Optionally derive a VM expression from the Python source
        // so that VmStaticRules can also run for pure Python tasks.
        val vmFromPython: Option[BeExpression] =
          if trimmed.nonEmpty && vmFromRequest.isEmpty then
            Try(PythonParser.parsePython(canonical)).toOption
          else None

        val vmForChecks = vmFromRequest.orElse(vmFromPython)

        (canonical, vmForChecks)

      // Case 2: only a VM expression is available (block program)
      case None =>
        val vmOpt = request.vmExpression
        val pythonFromVm =
          vmOpt
            .map(expr =>
              expr.getInLanguage(AppLanguage.Python, request.preferredHumanLanguage)
            )
            .getOrElse("")
        (pythonFromVm, vmOpt)

  /**
   * Run all static checks configured in the request:
   *  - VM rules (if we have a VM expression)
   *  - Python rules (on the original Python source, if available)
   */
  private def runStaticRules(
      request: BlockFeedbackRequest,
      vmExpressionOpt: Option[BeExpression],
      rawPython: String
  ): Seq[RuleResult] =
    val vmRuleResults: Seq[RuleResult] =
      if request.config.enableVmStaticChecks then
        vmExpressionOpt.map(VmStaticRules.runAll).getOrElse(Seq.empty)
      else Seq.empty

    // For Python rules we prefer the ORIGINAL student code if present,
    // not the VM-derived Python, so that PY_EMPTY etc. behave intuitively.
    val pythonSourceForRules: String =
      request.studentCodePython.getOrElse(rawPython)

    val pythonRuleResults: Seq[RuleResult] =
      if request.config.enablePythonStaticChecks then
        PythonStaticRules.runAll(pythonSourceForRules)
      else Seq.empty

    (vmRuleResults ++ pythonRuleResults).toList

  /**
   * Translate a RuleResult into a PythonTestResult.
   */
  private def ruleToTestResult(rule: RuleResult): PythonTestResult =
    PythonTestResult(
      name = s"${rule.category}:${rule.id}",
      passed = rule.passed,
      expected =
        if rule.passed then "Rule satisfied"
        else "Rule not satisfied",
      actual =
        if rule.passed then "OK"
        else rule.message,
      message = rule.details.orElse(Some(rule.message))
    )

  /**
   * Compute a normalized score and overall feedback status from all rules.
   *
   * Very simple logic:
   *  - empty code       -> score 0.0, status NOT_STARTET
   *  - no rules at all  -> score 1.0, status FINISHED
   *  - otherwise: 1 - (weighted failed / total weight), clamped to [0,1]
   */
  private def computeScoreAndStatus(
      rawPython: String,
      allRules: Seq[RuleResult]
  ): (Double, FeedbackStatus) =
    val trimmed = Option(rawPython).getOrElse("").trim

    if trimmed.isEmpty then
      (0.0, FeedbackStatus.NOT_STARTET)
    else if allRules.isEmpty then
      (1.0, FeedbackStatus.FINISHED)
    else
      val totalWeight  = allRules.map(_.severity.weight.toDouble).sum
      val failedWeight = allRules.filterNot(_.passed).map(_.severity.weight.toDouble).sum

      val rawScore =
        if totalWeight <= 0.0 then 1.0
        else math.max(0.0, 1.0 - failedWeight / totalWeight)

      val status =
        if failedWeight == 0.0 then FeedbackStatus.FINISHED
        else FeedbackStatus.IN_PROGRESS

      (rawScore, status)

  /**
   * Build a short summary text based on all static rules.
   * The wording differs depending on whether the origin was blocks or Python text.
   */
  private def buildSummary(
      origin: BlockStudentCodeOrigin,
      allRules: Seq[RuleResult]
  ): String =
    val programWord =
      origin match
        case BlockStudentCodeOrigin.Blocks     => "Blockprogramm"
        case BlockStudentCodeOrigin.PythonText => "Python-Programm"

    if allRules.isEmpty then
      s"Your $programWord has not been analyzed yet."
    else
      val total       = allRules.size
      val failedRules = allRules.filterNot(_.passed)
      val failed      = failedRules.size
      val errors      = failedRules.count(_.severity == RuleSeverity.Error)
      val warns       = failedRules.count(_.severity == RuleSeverity.Warning)

      val details =
        if errors == 0 && warns == 0 then ""
        else if errors > 0 && warns == 0 then s" ($errors error(s))."
        else if errors == 0 && warns > 0 then s" ($warns warning(s))."
        else s" ($errors error(s), $warns warning(s))."

      s"Your $programWord was analyzed. $failed of $total static checks reported issues.$details"
