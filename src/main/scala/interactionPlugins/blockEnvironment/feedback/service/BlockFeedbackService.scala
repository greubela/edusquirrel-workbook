package interactionPlugins.blockEnvironment.feedback

import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import contentmanagement.model.vm.code.BeExpression
import interactionPlugins.blockEnvironment.feedback.ai.{FetchProxyLlmClient, LlmClient, PromptTemplates, QualityGate}
import interactionPlugins.blockEnvironment.feedback.diagnosis.{DiagnosisAdapters, DiagnosisEngine}
import interactionPlugins.blockEnvironment.feedback.rules.{PythonStaticRules, VmStaticRules}
import interactionPlugins.blockEnvironment.feedback.ml.{BlockFeedbackSignals, DecisionLayer, FeatureExtractor, MlRouter, MlTrainingLogger}
import interactionPlugins.blockEnvironment.feedback.ui.FeedbackDemoElement
import interactionPlugins.pythonExercises.PythonRunStatus

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

/**
 * Central orchestration of the Python feedback pipeline.
 *
 * Responsibilities:
 *  - normalize the submitted Python source
 *  - derive a test plan from configuration and submission metadata
 *  - delegate runtime execution and build the user-facing feedback
 */
object BlockFeedbackService:

  private def maxIndentLevelFromPythonSource(rawPython: String, spacesPerLevel: Int = 4): Int =
    if rawPython == null || rawPython.isEmpty then 0
    else
      val normalized = rawPython.replace("\r\n", "\n")
      val indents =
        normalized.linesIterator
          .map { line =>
            val trimmed = line.dropWhile(c => c == ' ' || c == '\t')
            if trimmed.isEmpty then 0
            else
              val prefix = line.take(line.length - trimmed.length)
              val columns = prefix.foldLeft(0) { (acc, ch) =>
                if ch == '\t' then acc + spacesPerLevel else acc + 1
              }
              math.max(0, columns / math.max(1, spacesPerLevel))
          }
          .toSeq

      if indents.isEmpty then 0 else indents.max

  private def shouldUseProxyLlm: Boolean = {
    val hasWindow =
      try !js.isUndefined(js.Dynamic.global.selectDynamic("window")) && js.Dynamic.global.selectDynamic("window") != null
      catch case _: Throwable => false

    if hasWindow then true
    else {
      def readEnv(name: String): Option[String] =
        try {
          val process = js.Dynamic.global.selectDynamic("process")
          if js.isUndefined(process) || process == null then None
          else {
            val env = process.selectDynamic("env")
            if js.isUndefined(env) || env == null then None
            else {
              val v = env.selectDynamic(name)
              if js.isUndefined(v) || v == null then None else Some(v.toString)
            }
          }
        } catch {
          case _: Throwable => None
        }

      readEnv("LLM_PROXY_URL").exists(_.nonEmpty) ||
      readEnv("LLM_PROXY_ENABLED").exists(v => v == "1" || v.equalsIgnoreCase("true"))
    }
  }

  private lazy val proxyLlmClient: LlmClient = FetchProxyLlmClient.default()

  def withExerciseId(
      exerciseId: String,
      request: BlockFeedbackRequest
  ): BlockFeedbackRequest =
    request.copy(meta = request.meta.copy(exerciseId = Some(exerciseId)))

  def generateFeedbackForExercise(
      exerciseId: String,
      request: BlockFeedbackRequest
  )(using ExecutionContext): Future[BlockFeedbackResult] =
    generateFeedback(withExerciseId(exerciseId, request))

  def requestForExerciseId(
      exerciseId: String,
      studentProgram: BeExpression,
      submissionNr: Int,
      humanLanguage: HumanLanguage = AppLanguage.default()
  ): BlockFeedbackRequest =
    val definitionOpt = BlockFeedbackExerciseRegistry.byExerciseId.get(exerciseId)
    val exerciseText = definitionOpt
      .map(defn =>
        LanguageMap.mapBasedLanguageMap(
          defn.statementTranslations.collect { case (lang: HumanLanguage, text) => lang -> text }
        )
      )
      .getOrElse(
        LanguageMap.mapBasedLanguageMap(
          Map[HumanLanguage, String](humanLanguage -> "")
        )
      )

    BlockFeedbackRequest(
      exerciseText = exerciseText,
      studentCodePython = studentProgram,
      submissionNr = submissionNr,
      config = BlockFeedbackConfig.default,
      meta = BlockFeedbackMeta(exerciseId = Some(exerciseId)),
      humanLanguage = humanLanguage
    )

  def generateFeedbackForExerciseId(
      exerciseId: String,
      studentProgram: BeExpression,
      submissionNr: Int,
      humanLanguage: HumanLanguage = AppLanguage.default()
  )(using ExecutionContext): Future[BlockFeedbackResult] =
    generateFeedback(requestForExerciseId(exerciseId, studentProgram, submissionNr, humanLanguage))

  def generateFeedback(
    request: BlockFeedbackRequest
  )(using ExecutionContext): Future[BlockFeedbackResult] =
    val effectiveConfig =
      BlockFeedbackConfigProvider.resolveConfig(request.meta.exerciseId, request.config)
    val effectiveRequest =
      if effectiveConfig == request.config then request
      else request.copy(config = effectiveConfig)

    val testPlan = BlockFeedbackTestFactory.deriveTestPlan(effectiveRequest)

    val rawPython = effectiveRequest.pythonSource
    val pythonRules =
      if effectiveRequest.config.enablePythonStaticChecks then
        PythonStaticRules.runAll(rawPython, effectiveRequest.humanLanguage)
      else Nil
    val vmRules0 =
      if effectiveRequest.config.enableVmStaticChecks then
        VmStaticRules.runAll(effectiveRequest.studentCodePython, effectiveRequest.humanLanguage)
      else Nil

    // VM_MAX_NESTING is too noisy in pythonSourceOverride runs (Feedback Demo)
    val vmRules =
      if effectiveRequest.pythonSourceOverride.isDefined then
        vmRules0.filterNot(_.id == "VM_MAX_NESTING")
      else vmRules0

    val runtimeOutcomeFuture =
      if rawPython.trim.isEmpty then
        Future.successful(PythonRuntimeOutcome.empty)
      else if effectiveRequest.config.enableUnitTests then
        BlockFeedbackTestRunner.execute(effectiveRequest, testPlan)
      else
        Future.successful(PythonRuntimeOutcome.empty)

    runtimeOutcomeFuture.flatMap { outcome =>
      val baseSignals = BlockFeedbackSignals.from(effectiveRequest, pythonRules, vmRules, outcome)
      val weakDecision = DecisionLayer.heuristicRoute(baseSignals)

      // Optional: collect training data (features + weak label) for offline training.
      MlTrainingLogger.logIfEnabled(
        enabled = effectiveRequest.config.enableMlLogging,
        logUrl = effectiveRequest.config.mlLogUrl,
        request = effectiveRequest,
        weakDecision = weakDecision,
        features = FeatureExtractor.toMap(baseSignals)
      )

      // Optional: start model loading in background. If not ready yet, MlRouter will fall back.
      if effectiveRequest.config.routerMode == interactionPlugins.blockEnvironment.feedback.ml.RouterMode.Ml then
        MlRouter.ensureLoading(effectiveRequest.config.mlModelUrl)

      val decision = DecisionLayer.route(baseSignals, effectiveRequest.config.routerMode)
      val templateId = DecisionLayer.templateIdFor(decision.primaryIssue)
      val signals = baseSignals.copy(decision = Some(decision), templateId = Some(templateId))
      BlockFeedbackSignals.maybeDebugLog(signals)

      val diagnosis0 = DiagnosisEngine.build(effectiveRequest, testPlan, signals, decision)
      val diagnosis = DiagnosisAdapters.applyAdapters(diagnosis0, effectiveRequest, testPlan, signals, decision)


      val hasRuntimeOrTestIssue =
        outcome.runtimeError.exists(_.nonEmpty) ||
          outcome.runStatus.exists(_ != PythonRunStatus.Success) ||
          outcome.tests.exists(!_.passed)

      val allTestsPassed =
        effectiveRequest.config.enableUnitTests &&
          outcome.tests.nonEmpty &&
          outcome.tests.forall(_.passed)

      // When the ML router is confident the submission is correct, suppress the LLM
      // for failure diagnostics. But still allow a pass-case quality review when
      // all tests are green, so feedback can stay code-aware instead of generic.
      val mlSaysCorrect = decision.mlCorrectSignal || decision.primaryIssue == DecisionLayer.IssueType.CORRECT

      val llmFailureReviewEligible =
        effectiveRequest.config.enableAiSummary && hasRuntimeOrTestIssue && !mlSaysCorrect
      val llmPassReviewEligible = false
      val llmEligible = llmFailureReviewEligible && !llmPassReviewEligible
      val nameCheck = FunctionNameChecker.check(rawPython, testPlan, outcome.tests)
      val testPlanEffective = nameCheck.hintOption(effectiveRequest.humanLanguage) match
        case Some(hint) => testPlan.copy(derivedHints = Seq(hint) ++ testPlan.derivedHints)
        case None       => testPlan
      val studentHasNoFunction =
        !effectiveRequest.config.isScriptExercise &&
        !rawPython.linesIterator.exists(l => l.trim.startsWith("def "))
      val meaningfulLineCount = rawPython.linesIterator.count(l => l.trim.nonEmpty && !l.trim.startsWith("#"))
      val isTrivialScriptSubmission = effectiveRequest.config.isScriptExercise && meaningfulLineCount < 2
      val llmEligibleEffective = llmEligible && !nameCheck.hasMismatch && !studentHasNoFunction && !isTrivialScriptSubmission

      val visibleTestNames = testPlanEffective.visibleTests.map(_.name)

      def normalizeStudentFacingText(text: String): String =
        if text == null || text.isEmpty then ""
        else {
          val withoutTestNames =
            visibleTestNames
              .distinct
              .filter(_.nonEmpty)
              .foldLeft(text) { (acc, name) =>
                acc
                  .replaceAllLiterally(s"$name test", "the failing case")
              }

          // Reduce test-centric phrasing to student-centric phrasing.
          val rewritten =
            withoutTestNames
              // Never leak internal rule IDs.
              .replaceAll("\\b(?:PY|VM)_[A-Z0-9_]+\\s*:\\s*", "")
              .replaceAll("(?i)\\bthe test expects\\b", "Expected behavior")
              .replaceAll("(?i)\\brun (the )?([a-zA-Z0-9_\\-]+ )?test\\b", "run your code again on the failing case")
              .replaceAll("(?i)^and here's what went wrong:\\s*", "")

          rewritten.trim
        }

      val exerciseTextForLangRaw =
        effectiveRequest.exerciseText.getInLanguage(effectiveRequest.humanLanguage)
      val exerciseTextForLang =
        if exerciseTextForLangRaw.startsWith("[no ") then "" else exerciseTextForLangRaw

      val promptOpt =
        if llmEligibleEffective then
          val isGerman = effectiveRequest.humanLanguage == AppLanguage.German
          val hiddenTestDefMap = testPlanEffective.hiddenTests.map(t => t.name -> t).toMap
          val hiddenTestHintMap: Map[String, String] =
            outcome.tests
              .filterNot(_.passed)
              .filterNot(t => visibleTestNames.contains(t.name))
              .flatMap { t =>
                hiddenTestDefMap.get(t.name)
                  .flatMap(td => if isGerman then td.hintDE else td.hint)
                  .map(hint => t.name -> hint)
              }
              .toMap
          Some(
            PromptTemplates.buildPrompt(
              signals,
              diagnosis,
              decision,
              effectiveRequest.humanLanguage,
              visibleTestNames,
              exerciseTextForLang,
              rawPython,
              effectiveRequest.config.isScriptExercise,
              hiddenTestHintMap
            )
          )
        else None

      val fallbackCandidate = PromptTemplates.deterministicDraft(signals, decision, effectiveRequest.humanLanguage, effectiveRequest.config.isScriptExercise, visibleTestNames)

      def planWithCandidateOrFallback(candidate: String): BlockFeedbackTestPlan =
        promptOpt match
          case Some(prompt) =>
            val gated = QualityGate.enforce(candidate, prompt.constraints, prompt.testNames, rawPython)

            def truncateWords(text: String, maxWords: Int): String =
              if maxWords <= 0 then ""
              else
                val words = text.split("\\s+").toSeq.filter(_.nonEmpty)
                if words.size <= maxWords then text.trim
                else words.take(maxWords).mkString(" ").trim

            if gated.passed then
              testPlanEffective.copy(derivedHints = testPlanEffective.derivedHints ++ Seq(normalizeStudentFacingText(gated.finalText)))
            else if QualityGate.allowImperfectPassthrough then
              val passthroughText = truncateWords(
                normalizeStudentFacingText(gated.finalText),
                prompt.constraints.maxWords
              )
              if passthroughText.nonEmpty then
                testPlanEffective.copy(derivedHints = testPlanEffective.derivedHints ++ Seq(passthroughText))
              else testPlanEffective
            else
              // log
              FeedbackDemoElement.logEvent("AI currently not available, fallback text!")
              val fallbackGated = QualityGate.enforce(fallbackCandidate, prompt.constraints, prompt.testNames, rawPython)
              val fallbackText = truncateWords(
                normalizeStudentFacingText(fallbackGated.finalText),
                prompt.constraints.maxWords
              )
              if fallbackText.nonEmpty then
                testPlanEffective.copy(derivedHints = testPlanEffective.derivedHints ++ Seq(fallbackText))
              else testPlanEffective
          case None =>
            FeedbackDemoElement.logEvent("AI currently not available, another fallback!")
            testPlanEffective

      val basePlanHintsCount = testPlanEffective.derivedHints.size

      var llmRewriteCount: Int = 0
      var llmLastGateReasons: Seq[String] = Seq.empty

      val planWithAiFuture: Future[BlockFeedbackTestPlan] =
        if llmEligibleEffective && shouldUseProxyLlm then
          val prompt = promptOpt.get
          val logTag =
            effectiveRequest.meta.exerciseId.flatMap { id =>
              BlockFeedbackExerciseRegistry
                .byExerciseId
                .get(id)
                .map(_.titleTranslations.getOrElse(effectiveRequest.humanLanguage, id))
                .orElse(Some(id))
            }

          val failedTestNames = outcome.tests.filterNot(_.passed).map(_.name).filter(_.nonEmpty).mkString(", ")
          val llmDebugMeta = Map(
            "exerciseId"      -> effectiveRequest.meta.exerciseId.getOrElse(""),
            "primaryIssue"    -> decision.primaryIssue.toString,
            "templateId"      -> templateId,
            "testsTotal"      -> outcome.tests.size.toString,
            "testsFailed"     -> outcome.tests.count(!_.passed).toString,
            "failedTests"     -> failedTestNames,
            "hasRuntimeError" -> outcome.runtimeError.exists(_.trim.nonEmpty).toString
          )

          def llmWithRetries(currentPrompt: String, attempt: Int): Future[String] =
            proxyLlmClient
              .completeWithMeta(
                currentPrompt,
                logTag      = logTag,
                studentCode = Some(rawPython),
                debugMeta   = llmDebugMeta
              )
              .recover { case _ => fallbackCandidate }
              .flatMap { candidate =>
                val gated = QualityGate.enforce(candidate, prompt.constraints, prompt.testNames, rawPython)
                llmRewriteCount    = attempt - 1
                llmLastGateReasons = gated.reasons
                if gated.passed || attempt >= QualityGate.maxRewriteAttempts then
                  Future.successful(candidate)
                else
                  val correctionPrompt = QualityGate.buildCorrectionPrompt(
                    reasons          = gated.reasons,
                    originalPrompt   = prompt.prompt,
                    rejectedResponse = candidate,
                    constraints      = prompt.constraints,
                    attemptNr        = attempt + 1
                  )
                  llmWithRetries(correctionPrompt, attempt + 1)
              }

          llmWithRetries(prompt.prompt, attempt = 1)
            .map(planWithCandidateOrFallback)

        else if llmEligibleEffective then
          if llmPassReviewEligible then
            // No proxy LLM available: keep deterministic success messaging instead
            // of forcing a potentially misleading fallback rewrite.
            Future.successful(testPlanEffective)
          else
            Future.successful(planWithCandidateOrFallback(fallbackCandidate))
        else if hasRuntimeOrTestIssue && !nameCheck.hasMismatch then
          // name mismatch: rename hint already present, skip unrelated fallback draft
          Future.successful(testPlanEffective.copy(
            derivedHints = testPlanEffective.derivedHints ++ Seq(fallbackCandidate)
          ))
        else
          Future.successful(testPlanEffective)

      planWithAiFuture.map { planWithAi =>
        val feedback = BlockFeedbackFeedbackBuilder.buildFeedback(
          effectiveRequest,
          planWithAi,
          outcome,
          pythonRules,
          vmRules
        )

        val ruleHintsCount = pythonRules.count(!_.passed) + vmRules.count(!_.passed)
        val runtimeHintsCount =
          outcome.runtimeError.toSeq.size + outcome.tests.count(t => !t.passed && t.message.exists(_.trim.nonEmpty))

        val debug = FeedbackDebug(
          llmEligible = llmEligible,
          llmProxyAttempted = llmEligibleEffective && shouldUseProxyLlm,
          aiHintAdded = planWithAi.derivedHints.size > basePlanHintsCount,
          planHintsCount = planWithAi.derivedHints.size,
          ruleHintsCount = ruleHintsCount,
          runtimeHintsCount = runtimeHintsCount,
          testsTotal = outcome.tests.size,
          testsFailed = outcome.tests.count(!_.passed),
          hasRuntimeError = outcome.runtimeError.exists(_.trim.nonEmpty),
          hasEmptySource = rawPython.trim.isEmpty,
          primaryIssue = decision.primaryIssue.toString,
          templateId = Some(templateId),
          llmRewriteCount = llmRewriteCount,
          llmLastGateReasons = llmLastGateReasons,
          functionNameMismatch = nameCheck.suggestions.toSeq.sorted.map { case (ex, ac) => s"$ex->$ac" },
          rawRuntimeError = outcome.runtimeError.filter(_.trim.nonEmpty)
        )

        feedback.copy(debug = Some(debug))
      }
    }

