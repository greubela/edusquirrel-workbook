package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.config

import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.ml.RouterMode
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.model.BlockFeedbackMeta

/**
 * Resolves per-exercise configuration based on [[BlockFeedbackMeta.exerciseId]].
 *
 * callers only need to provide an
 * exercise id; the pipeline will pick the right config here.
 */
object BlockFeedbackConfigProvider:

  def resolveConfig(
      exerciseId: Option[String],
      fallback: BlockFeedbackConfig
  ): BlockFeedbackConfig =
    val base =
      exerciseId
        .flatMap(BlockFeedbackExerciseRegistry.byExerciseId.get)
        .map(_.config)
        .getOrElse(fallback)

    // allow passing ML-related settings via the fallback config
    // without having to edit every exercise config.
    val routerModeOverride =
      if fallback.routerMode != RouterMode.Heuristic then fallback.routerMode
      else base.routerMode

    base.copy(
      routerMode = routerModeOverride,
      enableMlLogging = base.enableMlLogging || fallback.enableMlLogging,
      mlLogUrl = base.mlLogUrl.orElse(fallback.mlLogUrl),
      mlModelUrl = base.mlModelUrl.orElse(fallback.mlModelUrl)
    )
