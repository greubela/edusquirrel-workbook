package interactionPlugins.blockEnvironment.feedback

import datastructures.core.vm.code.BeExpression
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
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
    studentCodePython: BeExpression,
    pythonSourceOverride: Option[String] = None,
    submissionNr: Int,
    config: BlockFeedbackConfig,
    meta: BlockFeedbackMeta = BlockFeedbackMeta(),
    humanLanguage: HumanLanguage = AppLanguage.default()
) {

    /** Preferred output language for human-readable texts. */
    def preferredHumanLanguage: HumanLanguage =
        humanLanguage

    /** Derives Python source from the VM expression tree. */
    def pythonSource: String =
        pythonSourceOverride
          .map(_.replace("\r\n", "\n"))
                    .getOrElse(studentCodePython.expressionIO.getInLanguage(AppLanguage.Python, preferredHumanLanguage))
}

/** Alias: internally we use the same type as the UI direction. */
type BlockFeedbackResult = UltrichsNewCoolFeedback
