package workbook.model.interaction

import workbook.model.feedback.grading.GradingResult
import workbook.model.states.InteractionState

trait Grader[EditorState <: InteractionState, GradingState <: InteractionState, G <: GradingResult[GradingState]] {
  def loadState(stateToLoad: GradingState): Unit

  def getCurrentState(): GradingState

  def gradeState(editorStateToGrade: EditorState, notifyOnGradingUpdate: G => Any): Unit
}