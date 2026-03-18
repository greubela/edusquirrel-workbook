package interactionPlugins.programmingExercise.pythonExercisesUnsorted

import workbook.model.feedback.FeedbackStatus
import workbook.model.feedback.FeedbackStatus.FINISHED
import workbook.model.feedback.grading.GradingGrade.{CORRECT, GRADING_ERROR, INCORRECT, PARTIALLY_CORRECT}
import workbook.model.feedback.grading.{GradingGrade, GradingResult}


/** Represents the current code inside the editor. */
final case class PythonEditorState(code: String) {
  def getStateAsString(): String = code

  def withCode(newCode: String): PythonEditorState = copy(code = newCode)
}

/** Captures the grader configuration at the moment a run starts. */
final case class PythonGradingState(codeSnapshot: String) {
  def getStateAsString(): String = codeSnapshot
}

enum PythonRunStatus {
  case Success, Failed, RuntimeError
}

enum PythonTestStatus {
  case Passed, Failed, Errored
}

final case class PythonTestResult(
                                   name: String,
                                   status: PythonTestStatus,
                                   isHidden: Boolean,
                                   message: Option[String],
                                   durationMs: Double,
                                   hint: Option[String]
                                 )

final case class PythonRunResult(
                                  status: PythonRunStatus,
                                  tests: Seq[PythonTestResult],
                                  stdout: String,
                                  stderr: String,
                                  error: Option[String],
                                  score: Double
                                )

final case class PythonGradingResult(
                                      stateWhenStarted: PythonGradingState,
                                      runResult: PythonRunResult,
                                      grade: GradingGrade,
                                      status: FeedbackStatus,
                                      normalizedScore: Double
                                    ) extends GradingResult[PythonGradingState]

object PythonGradingResult {

  def build(state: PythonGradingState, runResult: PythonRunResult): PythonGradingResult = {
    val grade = runResult.status match {
      case PythonRunStatus.Success if runResult.score == 1.0 => CORRECT
      case PythonRunStatus.Success if runResult.score > 0.0 => PARTIALLY_CORRECT
      case PythonRunStatus.Success => INCORRECT
      case PythonRunStatus.Failed if runResult.score > 0.0 => PARTIALLY_CORRECT
      case PythonRunStatus.Failed => INCORRECT
      case PythonRunStatus.RuntimeError => GRADING_ERROR
    }

    PythonGradingResult(state, runResult, grade, FINISHED, runResult.score)
  }
}
