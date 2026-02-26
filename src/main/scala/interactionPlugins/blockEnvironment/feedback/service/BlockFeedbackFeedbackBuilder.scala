package interactionPlugins.blockEnvironment.feedback

import contentmanagement.model.language.{AppLanguage, HumanLanguage}
import interactionPlugins.pythonExercises.PythonRunStatus
import interactionPlugins.blockEnvironment.feedback.rules.{RuleResult, RuleSeverity}
import workbook.model.feedback.FeedbackStatus

/**
 * Builds the final UI feedback from runtime outcomes and the derived test plan.
 */
object BlockFeedbackFeedbackBuilder:

  private def looksSmallBounded(exerciseText: String): Boolean =
    val text = Option(exerciseText).getOrElse("").trim
    if text.isEmpty then false
    else
      // Heuristic: if the statement mentions an explicit numeric range with a small upper bound,
      // performance warnings are usually confusing.
      val rangeRe = "(?s).*?(\\d{1,6})\\s*\\.\\.\\s*(\\d{1,6}).*".r
      val leRe = "(?s).*?(<=|≤)\\s*(\\d{1,6}).*".r
      val maxRe = "(?is).*?\\b(max|maximum|at most|höchstens|maximal)\\b[^\\d]{0,20}(\\d{1,6}).*".r

      def smallUpperBound(n: Int): Boolean = n > 0 && n <= 10000

      text match
        case rangeRe(_, hi) => hi.toIntOption.exists(smallUpperBound)
        case leRe(_, hi)    => hi.toIntOption.exists(smallUpperBound)
        case maxRe(_, hi)   => hi.toIntOption.exists(smallUpperBound)
        case _              => false

  private def isRedundantTestMessage(msg: String): Boolean =
    val lower = msg.trim.toLowerCase
    lower == "assertion failed" || lower.startsWith("assertion failed")

  private def countPrintStatements(rawPython: String): Int =
    Option(rawPython)
      .getOrElse("")
      .replace("\r\n", "\n")
      .split("\n", -1)
      .map(_.trim)
      .count(line => line.startsWith("print("))

  private def containsObviousPlaceholders(rawPython: String): Boolean =
    val text = Option(rawPython).getOrElse("")
    val lower = text.toLowerCase
    lower.contains("todo") ||
    lower.contains("fixme") ||
    text.linesIterator.exists(line =>
      val t = line.trim
      t == "pass" || t == "..." || t == "???"
    )

  private def looksInefficient(rawPython: String): Boolean =
    val lines =
      Option(rawPython)
        .getOrElse("")
        .replace("\r\n", "\n")
        .split("\n", -1)
        .toIndexedSeq

    val loopIndents =
      lines.flatMap { line =>
        val indent = line.takeWhile(_ == ' ').length
        val trimmed = line.dropWhile(_ == ' ')
        if trimmed.startsWith("for ") || trimmed.startsWith("while ") then Some(indent) else None
      }

    // Conservative: only flag when we see nested loops by indentation.
    loopIndents.size >= 2 && loopIndents.max > loopIndents.min

  def buildFeedback(
    request: BlockFeedbackRequest,
    plan: BlockFeedbackTestPlan,
    outcome: PythonRuntimeOutcome,
    pythonRules: Seq[RuleResult],
    vmRules: Seq[RuleResult]
  ): UltrichsNewCoolFeedback =
    val rawPython = request.pythonSource
    val hints0 =
      collectGeneralHints(
        rawPython = rawPython,
        tests = outcome.tests,
        runtimeError = outcome.runtimeError,
        planHints = plan.derivedHints,
        pythonRules = pythonRules,
        vmRules = vmRules,
        humanLanguage = request.humanLanguage
      )
    val (normalizedScore, status) =
      computeScoreAndStatus(
        rawPython,
        outcome.tests,
        outcome.normalizedScore,
        outcome.runStatus,
        request.config.enableUnitTests
      )
    val summary = buildSummary(outcome.tests, normalizedScore, request.humanLanguage)

    val allTestsPassed = request.config.enableUnitTests && outcome.tests.nonEmpty && outcome.tests.forall(_.passed)
    val hints =
      if hints0.nonEmpty then hints0
      else if allTestsPassed then
        val exerciseTextForLangRaw = request.exerciseText.getInLanguage(request.humanLanguage)
        val exerciseTextForLang = if exerciseTextForLangRaw.startsWith("[no ") then "" else exerciseTextForLangRaw
        val printCount = countPrintStatements(rawPython)
        val inefficient = looksInefficient(rawPython)
        val isScriptExercise = request.config.isScriptExercise
        val polished = (isScriptExercise || printCount == 0) && !inefficient && !containsObviousPlaceholders(rawPython)
        Seq(successTutorMessage(request.humanLanguage, exerciseTextForLang, printCount, inefficient, polished, isScriptExercise))
      else hints0

    val displayHints = if hints.nonEmpty then hints else Seq(summary)
    val displayTests = outcome.tests.map { test =>
      val msg = test.message.filter(_.trim.nonEmpty).getOrElse("")
      val expectedActual =
        BlockFeedbackTestResultFormatter.expectedActual(
          test = test,
          humanLanguage = request.humanLanguage,
          onlyWhenFailed = true
        )
      FeedbackTestDisplay(
        name = test.name,
        passed = test.passed,
        message = msg,
        expectedActual = expectedActual
      )
    }

    UltrichsNewCoolFeedback(
      summary = summary,
      tests = outcome.tests,
      generalHints = hints,
      displayHints = displayHints,
      displayTests = displayTests,
      allTestsPassed = allTestsPassed,
      rawPython = rawPython,
      status = status,
      normalizedScore = normalizedScore
    )

  private def successTutorMessage(
    humanLanguage: HumanLanguage,
    exerciseText: String,
    printCount: Int,
    inefficient: Boolean,
    polished: Boolean,
    isScript: Boolean
  ): String = {
    val isGerman = humanLanguage == AppLanguage.German
    val mentionPerf = inefficient && !looksSmallBounded(exerciseText)
    val hasSuspiciousPrints = !isScript && printCount > 0
    if isGerman then {
      val base =
        if polished then "Alle Tests sind grün. Richtig stark: Deine Lösung ist sauber und effizient."
        else "Sehr gut. Alle Tests sind grün."
      val prints = if hasSuspiciousPrints then " Entferne noch deine Debug-Prints, damit die Ausgabe sauber bleibt." else ""
      val perf =
        if mentionPerf then " Falls du das später auf größere Eingaben erweiterst: prüfe, ob du unnötig verschachtelte Schleifen hast." else ""
      (base + prints + perf).trim
    } else {
      val base =
        if polished then "All tests are green. Great job: your solution looks clean and efficient."
        else "Very good. All tests are green."
      val prints = if hasSuspiciousPrints then " Remove debug prints to keep the output clean." else ""
      val perf =
        if mentionPerf then " If you later scale this to larger inputs, check for unnecessary nested loops." else ""
      (base + prints + perf).trim
    }
  }

  private def collectGeneralHints(
    rawPython: String,
    tests: Seq[PythonTestResult],
    runtimeError: Option[String],
    planHints: Seq[String],
    pythonRules: Seq[RuleResult],
    vmRules: Seq[RuleResult],
    humanLanguage: HumanLanguage
  ): Seq[String] =
    val runtimeHints =
      runtimeError.toSeq ++
        tests
          .filterNot(_.passed)
          .flatMap(_.message)
          .filterNot(isRedundantTestMessage)
    val ruleHints = formatRuleHints(pythonRules) ++ formatRuleHints(vmRules)

    val distinctPlanHints = planHints.map(_.trim).filter(_.nonEmpty).distinct
    val distinctRuleHints = ruleHints.map(_.trim).filter(_.nonEmpty).distinct
    val distinctRuntimeHints = runtimeHints.map(_.trim).filter(_.nonEmpty).distinct

    if rawPython.trim.isEmpty then
      distinctPlanHints
    else if distinctPlanHints.nonEmpty then
      Seq(buildTutorMessage(distinctPlanHints, distinctRuleHints, distinctRuntimeHints, humanLanguage))
    else
      (distinctRuleHints ++ distinctRuntimeHints).distinct

  private def buildTutorMessage(
    planHints: Seq[String],
    ruleHints: Seq[String],
    runtimeHints: Seq[String],
    humanLanguage: HumanLanguage
  ): String =
    val isGerman = humanLanguage == AppLanguage.German
    val base = planHints.mkString("\n\n").trim

    val extras =
      Seq(
        if ruleHints.nonEmpty then
          val intro = if isGerman then "Zus\u00E4tzlich (kurze Checks):" else "Also (quick checks):"
          Some(intro + "\n" + ruleHints.map(h => s"- $h").mkString("\n"))
        else None,
        None
      ).flatten

    if extras.nonEmpty then (base + "\n\n" + extras.mkString("\n\n")).trim
    else base

  private def formatRuleHints(results: Seq[RuleResult]): Seq[String] =
    results
      .filterNot(_.passed)
      .filter(r => r.severity match
        case RuleSeverity.Warning | RuleSeverity.Error => true
        case RuleSeverity.Info                         => false
      )
      .map(r => s"${r.id}: ${r.message}")

  private def computeScoreAndStatus(
    rawPython: String,
    tests: Seq[PythonTestResult],
    runtimeScore: Option[Double],
    runtimeStatus: Option[PythonRunStatus],
    testsEnabled: Boolean
  ): (Double, FeedbackStatus) =
    val trimmed = rawPython.trim
    if trimmed.isEmpty then
      (0.0, FeedbackStatus.NOT_STARTET)
    else if !testsEnabled then
      (1.0, FeedbackStatus.IN_PROGRESS)
    else
      val score = runtimeScore.getOrElse(
        if tests.isEmpty then 0.0
        else math.max(0.0, math.min(1.0, tests.count(_.passed).toDouble / tests.size))
      )
      val status = runtimeStatus match
        case Some(PythonRunStatus.Success) if score >= 1.0 => FeedbackStatus.FINISHED
        case Some(PythonRunStatus.RuntimeError)            => FeedbackStatus.IN_PROGRESS
        case Some(PythonRunStatus.Success)                 => FeedbackStatus.IN_PROGRESS
        case Some(PythonRunStatus.Failed)                  => FeedbackStatus.IN_PROGRESS
        case None if tests.isEmpty                         => FeedbackStatus.IN_PROGRESS
        case None if tests.exists(!_.passed)               => FeedbackStatus.IN_PROGRESS
        case None                                          => FeedbackStatus.IN_PROGRESS
      (score, status)

  private def buildSummary(
    tests: Seq[PythonTestResult],
    normalizedScore: Double,
    humanLanguage: HumanLanguage
  ): String =
    val isGerman = humanLanguage == AppLanguage.German
    if tests.isEmpty then
      if isGerman then "Noch keine Tests ausgef\u00FChrt."
      else "No tests were executed yet."
    else
      val total = tests.size
      val passed = tests.count(_.passed)
      val percent = f"${normalizedScore * 100}%.1f%%"
      if isGerman then s"$passed von $total Tests bestanden ($percent)."
      else s"Passed $passed of $total tests ($percent)."
