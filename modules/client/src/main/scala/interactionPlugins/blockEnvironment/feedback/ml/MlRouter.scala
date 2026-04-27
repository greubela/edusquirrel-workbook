package interactionPlugins.blockEnvironment.feedback.ml

import scala.concurrent.ExecutionContext
import scala.scalajs.js

object MlRouter:

  @volatile private var model: Option[SoftmaxModel] = None
  @volatile private var loadStarted: Boolean = false

  private def hasWindow: Boolean =
    try !js.isUndefined(js.Dynamic.global.selectDynamic("window")) && js.Dynamic.global.selectDynamic("window") != null
    catch case _: Throwable => false

  /** Starts loading the model in the background (best effort). */
  def ensureLoading(modelUrl: Option[String]): Unit =
    if model.nonEmpty || loadStarted then return
    if !hasWindow then return

    val url = modelUrl.map(_.trim).filter(_.nonEmpty)
    if url.isEmpty then return

    loadStarted = true

    try
      val fetch = js.Dynamic.global.selectDynamic("fetch")
      if js.isUndefined(fetch) || fetch == null then return

      // fire-and-forget load
      fetch(url.get)
        .`then`((resp: js.Dynamic) => resp.text())
        .`then`((txt: js.Dynamic) => {
          val text = txt.toString
          model = Some(SoftmaxModel.fromJson(text))
          ()
        })
        .`catch`((_: js.Dynamic) => {
          // allow retry on next call
          loadStarted = false
          ()
        })
      ()
    catch
      case _: Throwable => loadStarted = false

  def isReady: Boolean = model.nonEmpty

  /**
   * The minimum ML probability required to act on a CORRECT prediction.
   * Set conservatively: we'd rather show a spurious hint than suppress a real issue.
   */
  private val CorrectMinConf: Double = 0.55

  /**
   * Heuristic issue types that are "soft" catch-alls  the ML CORRECT signal is
   * allowed to suppress these.  Hard-evidence issues (COMPILE_ERROR, EXCEPTION,
   * NONDETERMINISM …) are never suppressible.
   */
  private val softCatchAllIssues: Set[DecisionLayer.IssueType] = Set(
    DecisionLayer.IssueType.LOGIC_EDGE_CASE
  )

  def routeOrFallback(
    signals: BlockFeedbackSignals,
    fallback: DecisionLayer.Decision
  ): DecisionLayer.Decision =
    // Heuristic is authoritative for high-confidence, hard-evidence issues.
    val criticalHeuristicIssues = Set(
      DecisionLayer.IssueType.PERFORMANCE,
      DecisionLayer.IssueType.COMPILE_ERROR,
      DecisionLayer.IssueType.EXCEPTION_TYPE
    )
    if criticalHeuristicIssues.contains(fallback.primaryIssue) then return fallback

    model match
      case None => fallback
      case Some(m) =>
        val features = FeatureExtractor.toMap(signals)
        val (label, prob) = m.predictLabel(features)

        // "CORRECT" is not a DecisionLayer.IssueType, so we handle it specially.
        // Conditions to raise the mlCorrectSignal:
        //   1. ML confidence is sufficient (≥ CorrectMinConf)
        //   2. The heuristic fallback is only a soft catch-all (LOGIC_EDGE_CASE)
        //       never suppress when there is real evidence of an actual issue.
        //   3. No runtime error and no failing tests visible to us.
        if label == "CORRECT" then
          val heuristicIsSoft = softCatchAllIssues.contains(fallback.primaryIssue)
          val noRuntimeEvidence =
            fallback.evidence.isEmpty ||
            fallback.evidence.forall(_.key == "default")
          val noTestFailures = signals.runtimeOutcome.tests.forall(_.passed)
          if prob >= CorrectMinConf && heuristicIsSoft && noRuntimeEvidence && noTestFailures then
            // Keep the heuristic Decision intact but stamp the CORRECT signal.
            // This lets consumers ( BlockFeedbackService) skip LLM and
            // other non essential processing without altering the Issue label.
            return fallback.copy(
              mlCorrectSignal = true,
              topCauses = Seq(f"ml:CORRECT($prob%.2f)") ++ fallback.topCauses
            )
          else
            return fallback

        val issueOpt = DecisionLayer.IssueType.values.find(_.toString == label)
        issueOpt match
          case None => fallback
          case Some(issue) =>
            // Don't let ML claim IO_CONTRACT when there are no input() calls in the student code.
            if issue == DecisionLayer.IssueType.IO_CONTRACT && signals.inputCallCount == 0 then
              return fallback
            // Don't let ML override a specific heuristic finding with a weaker one.
            val heuristicIsSpecific = !softCatchAllIssues.contains(fallback.primaryIssue) &&
              !criticalHeuristicIssues.contains(fallback.primaryIssue)
            val mlIsWeaker = softCatchAllIssues.contains(issue)
            if heuristicIsSpecific && mlIsWeaker then return fallback

            val contributors = m.topContributors(features, label, k = 4)
            val causes =
              if contributors.isEmpty then Seq("ml")
              else contributors.map { case (k, v) => f"$k=$v%.3f" }

            val severity = issue match
              case DecisionLayer.IssueType.COMPILE_ERROR | DecisionLayer.IssueType.EXCEPTION_TYPE | DecisionLayer.IssueType.PERFORMANCE =>
                DecisionLayer.Severity.HIGH
              case DecisionLayer.IssueType.API_SIGNATURE | DecisionLayer.IssueType.IO_CONTRACT | DecisionLayer.IssueType.FORMAT_OUTPUT =>
                DecisionLayer.Severity.MEDIUM
              case _ =>
                DecisionLayer.Severity.LOW

            DecisionLayer.Decision(
              primaryIssue = issue,
              secondaryIssues = fallback.secondaryIssues,
              severity = severity,
              confidence = math.max(0.0, math.min(0.99, prob)),
              topCauses = causes,
              evidence = fallback.evidence
            )
