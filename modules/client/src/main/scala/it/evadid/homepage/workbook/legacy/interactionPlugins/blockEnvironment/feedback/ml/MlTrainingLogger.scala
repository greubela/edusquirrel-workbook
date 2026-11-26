package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.ml

import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.model.{BlockFeedbackMeta, BlockFeedbackRequest}
import scala.scalajs.js

/**
 * Logs (features, weak label) to enable offline training.
 *
 * - Weak label comes from the heuristic router.
 * - Logging is optional and should be disabled by default.
 */
object MlTrainingLogger:

  final case class Example(
    timestampEpochMillis: Long,
    exerciseId: Option[String],
    submissionNr: Int,
    weakLabel: String,
    weakConfidence: Double,
    features: Map[String, Double],
    meta: Map[String, String] = Map.empty
  )

  private def nowMillis(): Long =
    try js.Date.now().toLong
    catch case _: Throwable => System.currentTimeMillis()

  private def hasWindow: Boolean =
    try !js.isUndefined(js.Dynamic.global.selectDynamic("window")) && js.Dynamic.global.selectDynamic("window") != null
    catch case _: Throwable => false

  private def hasFetch: Boolean =
    try {
      val fetch = js.Dynamic.global.selectDynamic("fetch")
      !(js.isUndefined(fetch) || fetch == null)
    } catch {
      case _: Throwable => false
    }

  private def toJson(example: Example): String = {
    val d = js.Dictionary[js.Any](
      "timestampEpochMillis" -> example.timestampEpochMillis.toDouble,
      "exerciseId" -> example.exerciseId.orNull,
      "submissionNr" -> example.submissionNr,
      "weakLabel" -> example.weakLabel,
      "weakConfidence" -> example.weakConfidence,
      "features" -> js.Dictionary(example.features.map((k, v) => k -> (v: js.Any)).toSeq*)
    )

    if example.meta.nonEmpty then
      d.update("meta", js.Dictionary(example.meta.map((k, v) => k -> (v: js.Any)).toSeq*))

    js.JSON.stringify(d)
  }

  /**
   * If `logUrl` is provided, POSTs to it. Otherwise prints a JSON line to console.
   *
   * `weakLabelOverride`  when set, replaces the label derived from `weakDecision`.
   * Use this when the caller has ground-truth knowledge that overrides the heuristic
   * (e.g. a confirmed-correct solution that should be labelled "CORRECT", not
   * whatever the heuristic falls back to).
   */
  def logIfEnabled(
    enabled: Boolean,
    logUrl: Option[String],
    request: BlockFeedbackRequest,
    weakDecision: DecisionLayer.Decision,
    features: Map[String, Double],
    meta: Map[String, String] = Map.empty,
    weakLabelOverride: Option[String] = None
  ): Unit =
    if !enabled then return

    val effectiveLabel      = weakLabelOverride.getOrElse(weakDecision.primaryIssue.toString)
    val effectiveConfidence = if weakLabelOverride.isDefined then 1.0 else weakDecision.confidence

    val ex = Example(
      timestampEpochMillis = nowMillis(),
      exerciseId = request.meta.exerciseId,
      submissionNr = request.submissionNr,
      weakLabel = effectiveLabel,
      weakConfidence = effectiveConfidence,
      features = features,
      meta = meta
    )

    val jsonLine = toJson(ex)

    // In Node/Scala.js tests we can still POST if fetch exists.
    if !hasWindow && !hasFetch then
      println(jsonLine)
      return

    logUrl match
      case Some(url) if url.trim.nonEmpty =>
        try
          val fetch = js.Dynamic.global.selectDynamic("fetch")
          if js.isUndefined(fetch) || fetch == null then
            js.Dynamic.global.console.log(jsonLine)
          else
            val opts = js.Dynamic.literal(
              method = "POST",
              headers = js.Dictionary("content-type" -> "application/json"),
              body = jsonLine
            )
            fetch(url, opts)
            ()
        catch
          case _: Throwable => js.Dynamic.global.console.log(jsonLine)
      case _ =>
        try js.Dynamic.global.console.log(jsonLine)
        catch case _: Throwable => println(jsonLine)
