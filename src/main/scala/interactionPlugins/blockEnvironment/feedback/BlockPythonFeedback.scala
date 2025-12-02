package interactionPlugins.blockEnvironment.feedback

import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.model.vm.code.BeExpression
import interactionPlugins.blockEnvironment.feedback.{BlockFeedbackConfig, BlockFeedbackMeta, BlockFeedbackRequest, BlockFeedbackService, UltrichsNewCoolFeedback}

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
   *  - studentCode: either a block program (BeExpression) or raw Python source
   *  - submissionNr: sequential submission number
   *
   * When a block program is given, the Python source is derived internally.
   */
  def getFeedback(
    exerciseText: LanguageMap[HumanLanguage],
    currentLanguage: HumanLanguage,
    studentCode: BlockStudentCode,
    submissionNr: Int
  ): UltrichsNewCoolFeedback = {
    val (studentCodePythonOpt, vmExpressionOpt) =
      studentCode match
        case BlockStudentCode.FromBlocks(expr)   => (None, Some(expr))
        case BlockStudentCode.FromPython(source) => (Some(source), None)

    val request = BlockFeedbackRequest(
      exerciseText = exerciseText,
      studentCodePython = studentCodePythonOpt,
      vmExpression = vmExpressionOpt,
      submissionNr = submissionNr,
      config = BlockFeedbackConfig.default,
      meta = BlockFeedbackMeta(),
      origin = BlockStudentCode.originOf(studentCode)
    )

    BlockFeedbackService.generateFeedback(request)
  }

}
