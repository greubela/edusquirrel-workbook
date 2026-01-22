package interactionPlugins.blockEnvironment.feedback

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
    exerciseId
      .flatMap(BlockFeedbackExerciseRegistry.byExerciseId.get)
      .map(_.config)
      .getOrElse(fallback)
