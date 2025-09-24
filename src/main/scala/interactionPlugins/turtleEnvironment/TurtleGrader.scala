package interactionPlugins.turtleEnvironment

import workbook.model.feedback.FeedbackStatus
import workbook.model.feedback.FeedbackStatus.FINISHED
import workbook.model.feedback.grading.{GradingGrade, GradingResult}
import workbook.model.interaction.Grader

case class TurtleGradingFeedback(
  stateWhenStarted: TurtleGradingState,
  status: FeedbackStatus,
  grade: GradingGrade,
  coloredLines: List[ColoredTurtleLine],
  svg: String,
  missingLines: List[TurtleLineSegment],
  additionalLines: List[TurtleLineSegment]
) extends GradingResult[TurtleGradingState]

class TurtleGrader(expectedLines: List[TurtleLineSegment]) extends Grader[TurtleEditorState, TurtleGradingState, TurtleGradingFeedback] {

  private var currentState: TurtleGradingState = TurtleGradingState(TurtleProgramState.empty, expectedLines)

  override def loadState(stateToLoad: TurtleGradingState): Unit = currentState = stateToLoad

  override def getCurrentState(): TurtleGradingState = currentState

  override def gradeState(editorStateToGrade: TurtleEditorState, notifyOnGradingUpdate: TurtleGradingFeedback => Any): Unit = {
    val execution = TurtleProgramExecutor.execute(editorStateToGrade.program)
    val expected = currentState.expectedLines
    val missing = expected.filterNot(line => execution.lines.exists(_.approximatelyEquals(line)))
    val additional = execution.lines.filterNot(line => expected.exists(_.approximatelyEquals(line)))
    val matching = execution.lines.filter(line => expected.exists(_.approximatelyEquals(line)))

    val colored =
      matching.map(ColoredTurtleLine(_, "#009f4d")) ++
        missing.map(ColoredTurtleLine(_, "#ffa500")) ++
        additional.map(ColoredTurtleLine(_, "#ff4d4d"))

    val svg = execution.toColoredSvg(colored)
    val grade = if (missing.isEmpty && additional.isEmpty) GradingGrade.CORRECT else GradingGrade.INCORRECT
    val state = TurtleGradingState(editorStateToGrade.program, expected)
    val feedback = TurtleGradingFeedback(state, FINISHED, grade, colored, svg, missing, additional)
    notifyOnGradingUpdate(feedback)
  }
}
