package interactionPlugins.automaton

import workbook.model.feedback.FeedbackStatus
import workbook.model.feedback.FeedbackStatus.FINISHED
import workbook.model.feedback.grading.{GradingGrade, GradingResult}
import workbook.model.interaction.Grader

case class AutomatonGradingFeedback(
  stateWhenStarted: AutomatonGradingState,
  status: FeedbackStatus,
  grade: GradingGrade,
  results: List[AutomatonTestResult]
) extends GradingResult[AutomatonGradingState]

class AutomatonGrader extends Grader[AutomatonEditorState, AutomatonGradingState, AutomatonGradingFeedback] {

  private var currentState: AutomatonGradingState = AutomatonGradingState(
    AutomatonEditorState(AutomatonMode.Dfa, Vector.empty, Vector.empty),
    Nil,
    Nil
  )

  override def loadState(stateToLoad: AutomatonGradingState): Unit = currentState = stateToLoad

  override def getCurrentState(): AutomatonGradingState = currentState

  override def gradeState(editorStateToGrade: AutomatonEditorState, notifyOnGradingUpdate: AutomatonGradingFeedback => Any): Unit = {
    val tests = currentState.shouldAccept.map(AutomatonTestCase(_, expectedAccept = true)) ++
      currentState.shouldReject.map(AutomatonTestCase(_, expectedAccept = false))
    val results = tests.map { testCase =>
      val accepted = AutomatonSimulator.acceptsWord(editorStateToGrade, testCase.word)
      AutomatonTestResult(testCase.word, testCase.expectedAccept, accepted)
    }
    val passed = results.count(_.isCorrect)
    val grade = if (results.isEmpty) GradingGrade.UNKNOWN
    else if (passed == results.length) GradingGrade.CORRECT
    else if (passed == 0) GradingGrade.INCORRECT
    else GradingGrade.PARTIALLY_CORRECT
    val feedback = AutomatonGradingFeedback(currentState, FINISHED, grade, results)
    notifyOnGradingUpdate(feedback)
  }
}
