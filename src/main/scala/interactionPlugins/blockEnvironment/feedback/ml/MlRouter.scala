package interactionPlugins.blockEnvironment.feedback.ml

import scala.concurrent.ExecutionContext
import scala.scalajs.js

/**
 * Mini-ML router: uses an offline-trained softmax regression model.
 *
 * This router is designed to be swappable with the heuristic router without
 * changing the downstream pipeline.
 */
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

  def routeOrFallback(
    signals: BlockFeedbackSignals,
    fallback: DecisionLayer.Decision
  ): DecisionLayer.Decision =
    model match
      case None => fallback
      case Some(m) =>
        val features = FeatureExtractor.toMap(signals)
        val (label, prob) = m.predictLabel(features)

        val issueOpt = DecisionLayer.IssueType.values.find(_.toString == label)
        issueOpt match
          case None => fallback
          case Some(issue) =>
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
