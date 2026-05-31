package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.service

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.BlockFeedbackTestResultFormatter
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.model.{BlockFeedbackRequest, FeedbackTestDisplay, PythonRuntimeOutcome, PythonTestResult, UltrichsNewCoolFeedback}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.rules.{RuleResult, RuleSeverity}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.runtime.PythonRunStatus
import it.evadid.homepage.workbook.legacy.model.feedback.FeedbackStatus

/**
 * Builds the final UI feedback from runtime outcomes and the derived test plan.
 */
object BlockFeedbackFeedbackBuilder:

  private def sanitizeStudentMessage(text: String): String =
    val normalized =
      Option(text)
      .getOrElse("")
      .replace("–", " ")
      .replace("—", " ")
      .replace("--", " ")
    val numbered =
      normalized
        .split("\n", -1)
        .toSeq
        .foldLeft((Vector.empty[String], 1)) { case ((acc, idx), line) =>
          val t = line.trim
          if t.startsWith("• ") || t.startsWith("- ") || t.startsWith("* ") then
            (acc :+ s"$idx. ${t.drop(2).trim}", idx + 1)
          else
            (acc :+ t, idx)
        }
        ._1
        .mkString("\n")

    numbered
      .replaceAll("\\s{2,}", " ")
      .replaceAll("\\n\\s+", "\n")
      .trim

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

  private def findUnusedImports(rawPython: String, humanLanguage: HumanLanguage): Seq[String] =
    val lines = Option(rawPython).getOrElse("").replace("\r\n", "\n").split("\n", -1).toSeq
    val isGerman = humanLanguage == AppLanguage.German
    val importLines = lines.zipWithIndex.filter { case (line, _) =>
      val t = line.trim
      (t.startsWith("import ") || t.startsWith("from ")) && !t.startsWith("#")
    }
    importLines.flatMap { case (line, lineNum) =>
      val trimmed = line.trim
      val moduleName =
        if trimmed.startsWith("import ") then trimmed.drop(7).takeWhile(c => c.isLetterOrDigit || c == '_' || c == '.').split("\\.").head
        else if trimmed.startsWith("from ") then trimmed.drop(5).takeWhile(c => c.isLetterOrDigit || c == '_' || c == '.').split("\\.").head
        else ""
      if moduleName.nonEmpty && !rawPython.contains(s"$moduleName.") then
        if isGerman then
          Seq(s"Nicht verwendeter Import in Zeile ${lineNum + 1}: '$moduleName'")
        else
          Seq(s"Unused import on line ${lineNum + 1}: '$moduleName'")
      else Seq.empty
    }

  private def findUnusedVariables(rawPython: String, humanLanguage: HumanLanguage): Seq[String] =
    val lines = Option(rawPython).getOrElse("").replace("\r\n", "\n").split("\n", -1)
    val isGerman = humanLanguage == AppLanguage.German
    lines.zipWithIndex.flatMap { case (line, idx) =>
      val trimmed = line.trim
      if trimmed.matches("^_[a-zA-Z0-9_]+ *=.*") then
        if isGerman then
          Seq(s"Nicht verwendete Variable in Zeile ${idx + 1} (beginnt mit _)")
        else
          Seq(s"Unused variable on line ${idx + 1} (starts with _)")
      else Seq.empty
    }

  private def detectDeadCodeIssues(rawPython: String, humanLanguage: HumanLanguage): Seq[String] =
    findUnusedImports(rawPython, humanLanguage) ++ findUnusedVariables(rawPython, humanLanguage)

  /**
   * Detects top-level `def` blocks (indent=0) that are never called anywhere
   * outside their own body in the rest of the script.
   * These are "orphaned" definitions — irrelevant to the exercise.
   *
   * Example: a student pastes in `def merge_sorted(...)` when working on a
   * Fibonacci exercise — merge_sorted is never called, so it is irrelevant.
   */
  def detectOrphanedDefs(rawPython: String): Seq[String] =
    val lines = Option(rawPython).getOrElse("").replace("\r\n", "\n").split("\n", -1).toIndexedSeq
    val DefPat = """^def\s+([a-zA-Z_]\w*)\s*\(""".r

    val topLevelDefs: Seq[(String, Int)] = lines.zipWithIndex.collect {
      case (line, idx) if DefPat.findFirstMatchIn(line).isDefined =>
        (DefPat.findFirstMatchIn(line).get.group(1), idx)
    }

    if topLevelDefs.isEmpty then return Seq.empty

    topLevelDefs.flatMap { case (name, defIdx) =>
      val bodyEndRelative = lines.drop(defIdx + 1).indexWhere { l =>
        l.trim.nonEmpty && !l.headOption.exists(c => c == ' ' || c == '\t')
      }
      val bodyEndIdx = if bodyEndRelative < 0 then lines.length else defIdx + 1 + bodyEndRelative
      val bodyRange = defIdx until bodyEndIdx

      val callPattern = s"""\\b${java.util.regex.Pattern.quote(name)}\\s*\\(""".r
      val calledOutside = lines.zipWithIndex.exists { case (line, idx) =>
        !bodyRange.contains(idx) && callPattern.findFirstIn(line).isDefined
      }

      if calledOutside then None else Some(name)
    }

  /** Returns rawPython with the bodies of all orphaned top-level functions blanked out.
   *  Used so that perf/style analysis does not fire on irrelevant code.
   */
  private def stripOrphanedFunctionBodies(rawPython: String, orphanedNames: Seq[String]): String =
    if orphanedNames.isEmpty then return rawPython
    val lines = Option(rawPython).getOrElse("").replace("\r\n", "\n").split("\n", -1).toIndexedSeq
    val DefPat = """^def\s+([a-zA-Z_]\w*)\s*\(""".r

    val blankRanges = orphanedNames.flatMap { name =>
      lines.zipWithIndex.collectFirst {
        case (line, idx) if DefPat.findFirstMatchIn(line).exists(_.group(1) == name) =>
          val bodyEndRelative = lines.drop(idx + 1).indexWhere { l =>
            l.trim.nonEmpty && !l.headOption.exists(c => c == ' ' || c == '\t')
          }
          val bodyEndIdx = if bodyEndRelative < 0 then lines.length else idx + 1 + bodyEndRelative
          idx until bodyEndIdx
      }
    }

    val blankSet = blankRanges.flatMap(_.toSeq).toSet
    lines.zipWithIndex.map { case (line, idx) =>
      if blankSet.contains(idx) then "" else line
    }.mkString("\n")

  private def countNestedLoops(rawPython: String): Int =
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

    loopIndents.sliding(2).count {
      case Seq(outer, inner) => inner > outer
      case _                 => false
    }

  private def hasListComprehension(rawPython: String): Boolean =
    val text = Option(rawPython).getOrElse("")
    text.contains("[") && text.contains("for") && text.contains("in") &&
    text.linesIterator.exists(line =>
      line.trim.matches(".*\\[.*for .* in .*\\].*")
    )

  /** Returns the maximum loop nesting depth */
  private def maxLoopNestingDepth(rawPython: String): Int =
    val lines = Option(rawPython).getOrElse("").replace("\r\n", "\n").split("\n", -1).toIndexedSeq
    var stack = List.empty[Int]
    var maxDepth = 0
    lines.foreach { line =>
      val indent = line.takeWhile(_ == ' ').length
      val trimmed = line.dropWhile(_ == ' ')
      stack = stack.dropWhile(_ >= indent)
      if trimmed.startsWith("for ") || trimmed.startsWith("while ") then
        stack = indent :: stack
        if stack.size > maxDepth then maxDepth = stack.size
    }
    maxDepth

  private def analyzePerfOptimizations(rawPython: String, humanLanguage: HumanLanguage): Seq[String] =
    val nestedLoops = countNestedLoops(rawPython)
    val hasComprehension = hasListComprehension(rawPython)
    val isGerman = humanLanguage == AppLanguage.German

    val suggestions = scala.collection.mutable.ListBuffer[String]()
    if nestedLoops > 0 && !hasComprehension then
      val lines = Option(rawPython).getOrElse("").replace("\r\n", "\n").split("\n", -1).toIndexedSeq
      val loopLines = lines.zipWithIndex.flatMap { case (line, i) =>
        val indent = line.takeWhile(_ == ' ').length
        val t = line.dropWhile(_ == ' ')
        if t.startsWith("for ") || t.startsWith("while ") then Some((i, indent)) else None
      }
      val isSimpleForFor = if loopLines.size >= 2 then
        val (outerIdx, outerIndent) = loopLines(0)
        val (innerIdx, innerIndent) = loopLines(1)
        val outerIsFor = lines(outerIdx).dropWhile(_ == ' ').startsWith("for ")
        val innerIsFor = lines(innerIdx).dropWhile(_ == ' ').startsWith("for ")
        val innerBody = lines.drop(innerIdx + 1).takeWhile { l =>
          l.trim.isEmpty || l.takeWhile(_ == ' ').length > innerIndent
        }
        val hasBreak       = innerBody.exists(l => l.trim == "break" || l.trim.startsWith("break "))
        val appendCount    = innerBody.count(_.contains(".append("))
        val hasOnlyAppend  = appendCount == 1
        outerIsFor && innerIsFor && !hasBreak && hasOnlyAppend
      else false

      if isSimpleForFor then
        if isGerman then
          suggestions += "Du könntest verschachtelte Schleifen mit List Comprehensions vereinfachen"
        else
          suggestions += "You could simplify nested loops with list comprehensions"

    val depth = maxLoopNestingDepth(rawPython)
    if depth >= 3 then
      if isGerman then
        suggestions += s"Dein Code hat $depth verschachtelte Schleifen-Ebenen. Tiefe Schachtelung kann schwer lesbar und ineffizient sein überlege, ob du die Logik vereinfachen kannst."
      else
        suggestions += s"Your code has $depth levels of nested loops. Deep nesting can be hard to read and inefficient consider whether the logic can be simplified."

    suggestions.toSeq

  private def analyzeCodeStyle(rawPython: String, humanLanguage: HumanLanguage): Seq[String] =
    val printCount = countPrintStatements(rawPython)
    val isGerman = humanLanguage == AppLanguage.German

    val suggestions = scala.collection.mutable.ListBuffer[String]()

    if printCount > 5 then
      if isGerman then
        suggestions += "Du hast viele Debug Print Anweisungen. Überlege, welche wirklich nötig sind"
      else
        suggestions += "You have many debug print statements. Consider which ones are really necessary"
    else if printCount > 2 then
      if isGerman then
        suggestions += "Du hast noch Debug Print Anweisungen, die du vor dem Einreichen entfernen könntest"
      else
        suggestions += "You still have debug print statements that you could remove before submitting"

    suggestions.toSeq

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
    val summary = sanitizeStudentMessage(buildSummary(outcome.tests, normalizedScore, request.humanLanguage))

    val allTestsPassed = request.config.enableUnitTests && outcome.tests.nonEmpty && outcome.tests.forall(_.passed)
    val orphanedDefs = if request.config.isScriptExercise then detectOrphanedDefs(rawPython) else Seq.empty
    val hintsRaw =
      if hints0.nonEmpty then hints0
      else if allTestsPassed then
        val relevantPython = stripOrphanedFunctionBodies(rawPython, orphanedDefs)
        val deadCodeIssues = detectDeadCodeIssues(relevantPython, request.humanLanguage)
        val perfSuggestions = analyzePerfOptimizations(relevantPython, request.humanLanguage)
        val styleSuggestions = analyzeCodeStyle(relevantPython, request.humanLanguage)
        val isGerman = request.humanLanguage == AppLanguage.German
        val orphanHints: Seq[String] = if orphanedDefs.nonEmpty then
          val names = orphanedDefs.mkString(", ")
          val single = orphanedDefs.size == 1
          if isGerman then
            if single then Seq(s"Folgende Funktion ist definiert, wird aber nirgends aufgerufen und hat nichts mit dieser Aufgabe zu tun: $names. Du kannst sie entfernen.")
            else Seq(s"Folgende Funktionen sind definiert, werden aber nirgends aufgerufen und haben nichts mit dieser Aufgabe zu tun: $names. Du kannst sie entfernen.")
          else
            if single then Seq(s"The following function is defined but never called and is unrelated to this exercise: $names. You can remove it.")
            else Seq(s"The following functions are defined but never called and are unrelated to this exercise: $names. You can remove them.")
        else Seq.empty

        Seq(buildIntelligentPassFeedback(
          humanLanguage = request.humanLanguage,
          rawPython = rawPython,
          isScriptExercise = request.config.isScriptExercise,
          deadCodeIssues = deadCodeIssues ++ orphanHints,
          perfSuggestions = perfSuggestions,
          styleSuggestions = styleSuggestions
        ))
      else hints0

    val hints = hintsRaw.map(sanitizeStudentMessage).filter(_.nonEmpty)

    val displayHints = if hints.nonEmpty then hints else Seq(summary)
    val testDefMap = (plan.visibleTests ++ plan.hiddenTests).map(t => t.name -> t).toMap
    val visibleTestNames = plan.visibleTests.map(_.name).toSet
    val isGermanForDisplay = request.humanLanguage == AppLanguage.German
    val displayTests = outcome.tests.map { test =>
      val runtimeMsg = test.message.filter(_.trim.nonEmpty).getOrElse("")
      val isHidden = !visibleTestNames.contains(test.name)
      val msg =
        if runtimeMsg.nonEmpty then runtimeMsg
        else if !test.passed || isHidden then
          testDefMap.get(test.name)
            .flatMap(td => if isGermanForDisplay then td.hintDE else td.hint)
            .getOrElse("")
        else ""
      val expectedActual =
        if isHidden then None
        else
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

  private def buildIntelligentPassFeedback(
    humanLanguage: HumanLanguage,
    rawPython: String,
    isScriptExercise: Boolean,
    deadCodeIssues: Seq[String],
    perfSuggestions: Seq[String],
    styleSuggestions: Seq[String]
  ): String =
    val isGerman = humanLanguage == AppLanguage.German
    val baseline =
      if isGerman then "Dein Code funktioniert korrekt. Alle Tests sind grün."
      else "Your code works correctly. All tests pass."
    val allIssues = deadCodeIssues ++ perfSuggestions ++ styleSuggestions
    if allIssues.isEmpty then
      if isGerman then baseline + " Saubere, gut lesbare Lösung."
      else baseline + " Clean, well-written solution."
    else
      val improvements = scala.collection.mutable.ListBuffer[String]()
      deadCodeIssues.headOption.foreach { issue =>
        if isGerman then improvements += s"Eine kleine Aufräumaktion: $issue"
        else improvements += s"A small cleanup: $issue"
      }
      styleSuggestions.headOption.foreach { suggestion =>
        improvements += suggestion
      }
      perfSuggestions.headOption.foreach { suggestion =>
        improvements += suggestion
      }
      val improvementText =
        if improvements.size == 1 then
          if isGerman then "\n\nEine kleine Anmerkung: " + improvements.head
          else "\n\nOne small note: " + improvements.head
        else if improvements.size >= 2 then
          val numbered = improvements.take(2).zipWithIndex.map { case (msg, idx) => s"${idx + 1}. $msg" }.mkString("\n")
          if isGerman then "\n\nKleine Verbesserungen:\n" + numbered
          else "\n\nSmall improvements:\n" + numbered
        else ""
      baseline + improvementText

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
    def sanitizeRuleMessage(msg: String): String =
      Option(msg)
        .getOrElse("")
        .replaceAll("(^|\\n)\\s*(?:PY|VM)_[A-Z0-9_]+\\s*:\\s*", "$1")
        .replaceAll("\\b(?:PY|VM)_[A-Z0-9_]+\\s*:\\s*", "")
        .trim

    results
      .filterNot(_.passed)
      .filter(r => r.severity match
        case RuleSeverity.Warning | RuleSeverity.Error => true
        case RuleSeverity.Info                         => false
      )
      .map(r => sanitizeRuleMessage(r.message))
      .filter(_.nonEmpty)

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
