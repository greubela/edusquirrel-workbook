package interactionPlugins.blockEnvironment.programming

import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.model.vm.code.BeExpression
import interactionPlugins.blockEnvironment.feedback.{
  BlockFeedbackConfig,
  BlockFeedbackMeta,
  BlockFeedbackRequest,
  BlockFeedbackService,
  UltrichsNewCoolFeedback
}

/**
 * Public entry point for the block-Python feedback pipeline.
 *
 * This facade hides the internal architecture (service, rules, tests)
 * and exposes simple entry points.
 */
object BlockPythonFeedback {

  /**
   * Interface suggested by the professor:
   *
   *  - exerciseText: task description in multiple languages
   *  - studentCode: block program (VM model)
   *  - submissionNr: sequential submission number
   *
   * The Python code is derived internally from the BeExpression.
   */
  def getFeedback(
    exerciseText: LanguageMap[HumanLanguage],
    studentCode: BeExpression,
    submissionNr: Int
  ): UltrichsNewCoolFeedback = {
    val request = BlockFeedbackRequest(
      exerciseText = exerciseText,
      studentCodePython = None,
      vmExpression = Some(studentCode),
      submissionNr = submissionNr,
      config = BlockFeedbackConfig.default,
      meta = BlockFeedbackMeta()
    )

    BlockFeedbackService.generateFeedback(request)
  }

  /**
   * Alternative facade when the Python code is already pre-generated.
   * This is especially interesting for your backend pipeline.
   */
  def getFeedbackFromPython(
    exerciseText: LanguageMap[HumanLanguage],
    studentCodePython: String,
    submissionNr: Int
  ): UltrichsNewCoolFeedback = {
    val request = BlockFeedbackRequest(
      exerciseText = exerciseText,
      studentCodePython = Some(studentCodePython),
      vmExpression = None,
      submissionNr = submissionNr,
      config = BlockFeedbackConfig.default,
      meta = BlockFeedbackMeta()
    )

    BlockFeedbackService.generateFeedback(request)
  }
}
