package interactionPlugins.blockEnvironment.feedback

import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.model.vm.code.BeExpression
import scala.concurrent.{ExecutionContext, Future}

/**
 * Public entry point for the Python feedback pipeline.
 */
object BlockPythonFeedback:

  /**
   * Run the configured feedback pipeline for a Python submission.
   *
   * @param exerciseText localized versions of the exercise description
   * @param studentProgram the student's program as VM expression tree
   * @param submissionNr submission counter (for logging/analytics)
   */
  def getFeedback(
    exerciseText: LanguageMap[HumanLanguage],
    currentLanguage: HumanLanguage,
    studentProgram: BeExpression,
    submissionNr: Int
  )(using ExecutionContext): Future[UltrichsNewCoolFeedback] =
    val request = BlockFeedbackRequest(
      exerciseText = exerciseText,
      studentCodePython = studentProgram,
      submissionNr = submissionNr,
      config = BlockFeedbackConfig.default,
      meta = BlockFeedbackMeta(),
      humanLanguage = currentLanguage
    )

    BlockFeedbackService.generateFeedback(request)
