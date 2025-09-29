package interactionPlugins.blockProgramming

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

class TurtleGrader(
  expectedLines: List[TurtleLineSegment],
  settings: TurtleSegmentMatcher.Settings = TurtleSegmentMatcher.DefaultSettings
) extends Grader[TurtleEditorState, TurtleGradingState, TurtleGradingFeedback] {

  private val canonicalExpected = TurtleSegmentMatcher.canonicalize(expectedLines, settings)

  private var currentState: TurtleGradingState = TurtleGradingState(TurtleProgramState.empty, canonicalExpected)

  override def loadState(stateToLoad: TurtleGradingState): Unit =
    currentState = stateToLoad.copy(expectedLines = TurtleSegmentMatcher.canonicalize(stateToLoad.expectedLines, settings))

  override def getCurrentState(): TurtleGradingState = currentState

  override def gradeState(editorStateToGrade: TurtleEditorState, notifyOnGradingUpdate: TurtleGradingFeedback => Any): Unit = {
    val execution = TurtleProgramExecutor.execute(editorStateToGrade.program)
    val expected = currentState.expectedLines
    val actualSegments = TurtleSegmentMatcher.canonicalize(execution.lines, settings)
    val matchResult = TurtleSegmentMatcher.matchSegments(expected, actualSegments, settings)

    val combinedSegments = matchResult.overlapSegments ++ matchResult.expectedOnlySegments ++ matchResult.actualOnlySegments
    val view = TurtleGeometry.viewForSegments(combinedSegments, TurtleGeometry.DefaultMargin)

    val translateBy = view.offset
    val normalizedMatched = matchResult.overlapSegments.map(segment => TurtleGeometry.translate(segment, translateBy))
    val normalizedExpectedOnly = matchResult.expectedOnlySegments.map(segment => TurtleGeometry.translate(segment, translateBy))
    val normalizedActualOnly = matchResult.actualOnlySegments.map(segment => TurtleGeometry.translate(segment, translateBy))

    val colored =
      normalizedExpectedOnly.map(ColoredTurtleLine(_, "#ffa500")) ++
        normalizedActualOnly.map(ColoredTurtleLine(_, "#ff4d4d")) ++
        normalizedMatched.map(ColoredTurtleLine(_, "#009f4d"))

    val svg = TurtleSvgRenderer.render(colored, view.width, view.height, strokeWidth = 3.0)
    val grade =
      if (matchResult.expectedOnlySegments.isEmpty && matchResult.actualOnlySegments.isEmpty)
        GradingGrade.CORRECT
      else
        GradingGrade.INCORRECT
    val state = TurtleGradingState(editorStateToGrade.program, expected)
    val feedback = TurtleGradingFeedback(state, FINISHED, grade, colored, svg, normalizedExpectedOnly, normalizedActualOnly)
    notifyOnGradingUpdate(feedback)
  }
}
