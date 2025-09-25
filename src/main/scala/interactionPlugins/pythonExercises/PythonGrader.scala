package interactionPlugins.pythonExercises

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import workbook.model.feedback.FeedbackStatus
import workbook.model.feedback.scaffolding.BasicVariableScaffoldingResult
import workbook.model.interaction.{Grader, Scaffolder}
import workbook.model.states.Stateless

final case class PythonScaffolder() extends Scaffolder[Stateless, BasicVariableScaffoldingResult[String, Stateless]] {

  private var currentState: Stateless = Stateless.StatelessInstance

  override def loadState(stateToLoad: Stateless): Unit = currentState = stateToLoad

  override def getCurrentState(): Stateless = currentState

  override def generateFeedback(notifyOnGradingUpdate: BasicVariableScaffoldingResult[String, Stateless] => Any): Unit = {
    val result = BasicVariableScaffoldingResult(
      stateWhenStarted = currentState,
      variable = "Scaffolding for Python exercises is not yet implemented.",
      status = FeedbackStatus.FINISHED
    )
    notifyOnGradingUpdate(result)
  }
}

final case class PythonGrader(exerciseContent: PythonExerciseContent)
    extends Grader[PythonEditorState, PythonGradingState, PythonGradingResult] {

  private var currentState: PythonGradingState = PythonGradingState(exerciseContent.starterCode)

  override def loadState(stateToLoad: PythonGradingState): Unit = currentState = stateToLoad

  override def getCurrentState(): PythonGradingState = currentState

  override def gradeState(
      editorStateToGrade: PythonEditorState,
      notifyOnGradingUpdate: PythonGradingResult => Any
  ): Unit = {
    val state = PythonGradingState(editorStateToGrade.code)
    currentState = state
    val request = PythonRunRequest(
      code = editorStateToGrade.code,
      visibleTests = exerciseContent.visibleTests,
      hiddenTests = exerciseContent.hiddenTests,
      fixtures = exerciseContent.fixtures,
      packages = exerciseContent.packages,
      timeoutMs = exerciseContent.timeoutMs
    )

    PythonRuntimeService
      .run(request)
      .foreach { runResult =>
        val result = PythonGradingResult.build(state, runResult)
        notifyOnGradingUpdate(result)
      }
  }
}
