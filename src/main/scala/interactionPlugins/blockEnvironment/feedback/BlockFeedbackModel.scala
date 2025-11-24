package interactionPlugins.blockEnvironment.feedback

import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import contentmanagement.model.vm.code.BeExpression

/**
 * Metadata for a feedback invocation (exercise ID, user ID, etc.).
 * Optional useful later for logging/analytics.
 */
final case class BlockFeedbackMeta(
    exerciseId: Option[String] = None,
    userId: Option[String] = None
)

/**
 * Neutral request type for the feedback pipeline.
 * Decouples the public API from the internal orchestration.
 */
final case class BlockFeedbackRequest(
    exerciseText: LanguageMap[HumanLanguage],
    studentCodePython: Option[String],
    vmExpression: Option[BeExpression],
    submissionNr: Int,
    config: BlockFeedbackConfig,
    meta: BlockFeedbackMeta = BlockFeedbackMeta()
    origin: BlockStudentCodeOrigin = BlockStudentCodeOrigin.Blocks
) {

    /** Preferred output language for human-readable texts. */
    def preferredHumanLanguage: HumanLanguage =
        AppLanguage.default()
}

/** Alias: internally we use the same type as the UI direction. */
type BlockFeedbackResult = UltrichsNewCoolFeedback
