package interactionPlugins.blockEnvironment.feedback

import datastructures.core.vm.code.BeExpression
import it.evadid.core.datastructures.language.AppLanguage.*

import scala.concurrent.{ExecutionContext, Future}
/**
 * Public entry point for the Python feedback pipeline.
 */
object BlockPythonFeedback:

  /**
   * Public entry point for the feedback pipeline.
   *
   * Exercises (title/statement/config/tests) are defined in
   * [[BlockFeedbackExerciseRegistry]] and selected by id.
   */
  def getFeedback(
    exerciseId: String,
    currentLanguage: HumanLanguage,
    studentProgram: BeExpression,
    submissionNr: Int
  )(using ExecutionContext): Future[UltrichsNewCoolFeedback] =
    BlockFeedbackService.generateFeedbackForExerciseId(
      exerciseId = exerciseId,
      studentProgram = studentProgram,
      submissionNr = submissionNr,
      humanLanguage = currentLanguage
    )

