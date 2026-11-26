package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.exercise

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.config.BlockFeedbackConfig
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
