package interactionPlugins.blockEnvironment.feedback

import contentmanagement.model.language.AppLanguage

/**
 * Backend-oriented exercise definition for the feedback pipeline.
 *
 * Separates didactic content (statement translations) from evaluation/feedback
 * configuration.
 */
final case class FeedbackExerciseDefinition(
    id: String,
    titleTranslations: Map[AppLanguage, String] = Map.empty,
    statementTranslations: Map[AppLanguage, String] = Map.empty,
    config: BlockFeedbackConfig
)
