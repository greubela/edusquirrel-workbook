package interactionPlugins.blockEnvironment.feedback

import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import contentmanagement.model.vm.code.BeExpression
import interactionPlugins.blockEnvironment.feedback.ai.{FetchProxyLlmClient, LlmClient, PromptTemplates, QualityGate}
import interactionPlugins.blockEnvironment.feedback.rules.{PythonStaticRules, VmStaticRules}
import interactionPlugins.blockEnvironment.feedback.ml.{BlockFeedbackSignals, DecisionLayer}
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import org.scalajs.dom

/**
 * Central orchestration of the Python feedback pipeline.
 *
 * Responsibilities:
 *  - normalize the submitted Python source
 *  - derive a test plan from configuration and submission metadata
 *  - delegate runtime execution and build the user-facing feedback
 */
object BlockFeedbackService:

  private def debugAiEnabled: Boolean = {
    try
      val search = Option(dom.window.location.search).getOrElse("")
      search.contains("debugAi=1") || search.contains("debugAi=true")
    catch
      case _: Throwable => false
  }


  private def shouldUseProxyLlm: Boolean = {
    val hasWindow =
      try !js.isUndefined(js.Dynamic.global.selectDynamic("window")) && js.Dynamic.global.selectDynamic("window") != null
      catch case _: Throwable => false

    if hasWindow then true
    else {
      // In Node/Scala.js tests: require explicit opt-in.
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

  /** Pure helper to attach the exercise id to a request. */
  def withExerciseId(
      exerciseId: String,
      request: BlockFeedbackRequest
  ): BlockFeedbackRequest =
    request.copy(meta = request.meta.copy(exerciseId = Some(exerciseId)))

  /**
   * Backend entry API: sets [[BlockFeedbackMeta.exerciseId]] automatically so
   * per-exercise configuration lookup (Option B) can work.
   */
  def generateFeedbackForExercise(
      exerciseId: String,
      request: BlockFeedbackRequest
  )(using ExecutionContext): Future[BlockFeedbackResult] =
    generateFeedback(withExerciseId(exerciseId, request))

  /**
   * Build a request solely from our feedback exercise definitions.
   *
   * If the exercise id is unknown, falls back to default config and an empty
   * exercise text (but still stores the id in meta for observability).
   */
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
      // Always use default as fallback; the provider resolves per-exercise config
      // based on meta.exerciseId.
      config = BlockFeedbackConfig.default,
      meta = BlockFeedbackMeta(exerciseId = Some(exerciseId)),
      humanLanguage = humanLanguage
    )

  /**
   * Convenience backend entry: feedback by exercise id using the registry.
   */
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
    val debugEnabled = debugAiEnabled
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
    val vmRules =
      if effectiveRequest.config.enableVmStaticChecks then
        VmStaticRules.runAll(effectiveRequest.studentCodePython, effectiveRequest.humanLanguage)
      else Nil

    val runtimeOutcomeFuture =
      if effectiveRequest.config.enableUnitTests then
        BlockFeedbackTestRunner.execute(effectiveRequest, testPlan)
      else
        Future.successful(PythonRuntimeOutcome.empty)

    runtimeOutcomeFuture.flatMap { outcome =>
      val baseSignals = BlockFeedbackSignals.from(effectiveRequest, pythonRules, vmRules, outcome)
      val decision = DecisionLayer.route(baseSignals)
      val templateId = DecisionLayer.templateIdFor(decision.primaryIssue)
      val signals = baseSignals.copy(decision = Some(decision), templateId = Some(templateId))
      BlockFeedbackSignals.maybeDebugLog(signals)

      val llmEligible =
        effectiveRequest.config.enableAiSummary &&
          (decision.primaryIssue == DecisionLayer.IssueType.FORMAT_OUTPUT ||
            decision.primaryIssue == DecisionLayer.IssueType.IO_CONTRACT ||
            decision.primaryIssue == DecisionLayer.IssueType.INCOMPLETE_IMPLEMENTATION ||
            decision.primaryIssue == DecisionLayer.IssueType.BOUNDARY_CONDITION ||
            decision.primaryIssue == DecisionLayer.IssueType.EXCEPTION_TYPE ||
            decision.primaryIssue == DecisionLayer.IssueType.LOGIC_EDGE_CASE) &&
          decision.confidence >= 0.75

      val visibleTestNames = testPlan.visibleTests.map(_.name)

      val exerciseTextForLangRaw =
        effectiveRequest.exerciseText.getInLanguage(effectiveRequest.humanLanguage)
      val exerciseTextForLang =
        if exerciseTextForLangRaw.startsWith("[no ") then "" else exerciseTextForLangRaw

      val promptOpt =
        if llmEligible then
          Some(
            PromptTemplates.buildPrompt(
              signals,
              decision,
              effectiveRequest.humanLanguage,
              visibleTestNames,
              exerciseTextForLang,
              rawPython
            )
          )
        else None

      def planWithCandidate(candidate: String): BlockFeedbackTestPlan =
        promptOpt match
          case Some(prompt) =>
            val gated = QualityGate.enforce(candidate, prompt.constraints, prompt.testNames)
            if gated.passed then
              testPlan.copy(derivedHints = testPlan.derivedHints ++ Seq(gated.finalText))
            else testPlan
          case None => testPlan

      val fallbackCandidate = PromptTemplates.deterministicDraft(signals, decision, effectiveRequest.humanLanguage)

      def planWithCandidateOrFallback(candidate: String): (BlockFeedbackTestPlan, Option[String]) =
        promptOpt match
          case Some(prompt) =>
            val gated = QualityGate.enforce(candidate, prompt.constraints, prompt.testNames)
            if gated.passed then
              val dbg =
                if !debugEnabled then None
                else
                  Some(
                    Seq(
                      s"llmEligible=$llmEligible",
                      s"shouldUseProxyLlm=${shouldUseProxyLlm}",
                      s"primaryIssue=${decision.primaryIssue}",
                      f"confidence=${decision.confidence}%.2f",
                      s"templateId=$templateId",
                      s"visibleTests=${visibleTestNames.mkString(",")}",
                      s"gatePassed=true",
                      s"gateReasons=",
                      s"usedFallback=false"
                    ).mkString("\n")
                  )
              (testPlan.copy(derivedHints = testPlan.derivedHints ++ Seq(gated.finalText)), dbg)
            else
              def truncateWords(text: String, maxWords: Int): String =
                if maxWords <= 0 then ""
                else
                  val words = text.split("\\s+").toSeq.filter(_.nonEmpty)
                  if words.size <= maxWords then text.trim
                  else words.take(maxWords).mkString(" ").trim

              val fallbackGated = QualityGate.enforce(fallbackCandidate, prompt.constraints, prompt.testNames)
              val fallbackText = truncateWords(fallbackGated.finalText, prompt.constraints.maxWords)
              val plan2 =
                if fallbackText.nonEmpty then
                  testPlan.copy(derivedHints = testPlan.derivedHints ++ Seq(fallbackText))
                else testPlan

              val dbg =
                if !debugEnabled then None
                else
                  Some(
                    Seq(
                      s"llmEligible=$llmEligible",
                      s"shouldUseProxyLlm=${shouldUseProxyLlm}",
                      s"primaryIssue=${decision.primaryIssue}",
                      f"confidence=${decision.confidence}%.2f",
                      s"templateId=$templateId",
                      s"visibleTests=${visibleTestNames.mkString(",")}",
                      s"gatePassed=false",
                      s"gateReasons=${gated.reasons.mkString(",")}",
                      s"fallbackGatePassed=${fallbackGated.passed}",
                      s"fallbackGateReasons=${fallbackGated.reasons.mkString(",")}",
                      s"usedFallback=true"
                    ).mkString("\n")
                  )

              (plan2, dbg)
          case None =>
            (testPlan, None)

      val planWithAiFuture: Future[(BlockFeedbackTestPlan, Option[String])] =
        if llmEligible && shouldUseProxyLlm then
          val prompt = promptOpt.get
          proxyLlmClient
            .complete(prompt.prompt)
            .map(resp => ("proxy", resp))
            .recover { case _ => ("proxy_error", fallbackCandidate) }
            .map { case (source, text) =>
              val (plan, dbg0) = planWithCandidateOrFallback(text)
              val dbg =
                if !debugEnabled then None
                else {
                  val prefix = s"candidateSource=$source"
                  Some(dbg0.fold(prefix)(d => s"$prefix\n$d"))
                }
              (plan, dbg)
            }
        else if llmEligible then
          val (plan, dbg0) = planWithCandidateOrFallback(fallbackCandidate)
          val dbg =
            if !debugEnabled then None
            else {
              val prefix = "candidateSource=deterministic"
              Some(dbg0.fold(prefix)(d => s"$prefix\n$d"))
            }
          Future.successful((plan, dbg))
        else
          val dbg =
            if !debugEnabled then None
            else
              Some(
                Seq(
                  s"llmEligible=$llmEligible",
                  s"enableAiSummary=${effectiveRequest.config.enableAiSummary}",
                  s"primaryIssue=${decision.primaryIssue}",
                  f"confidence=${decision.confidence}%.2f",
                  s"templateId=$templateId",
                  s"visibleTests=${visibleTestNames.mkString(",")}",
                  "candidateSource=none"
                ).mkString("\n")
              )
          Future.successful((testPlan, dbg))

      planWithAiFuture.map { case (planWithAi, dbg) =>
        BlockFeedbackFeedbackBuilder.buildFeedback(
          effectiveRequest,
          planWithAi,
          outcome,
          pythonRules,
          vmRules,
          debugAi = dbg
        )
      }
    }

