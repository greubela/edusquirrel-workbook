package workbook.model.interaction.full

import workbook.model.feedback.grading.GradingResult
import workbook.model.feedback.scaffolding.ScaffoldingResult
import workbook.model.interaction.{Grader, Scaffolder}
import workbook.model.states.InteractionState
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

trait HtmlFullInteractionModel[
  EditorState <: InteractionState, ScaffoldingState <: InteractionState, GradingState <: InteractionState,
  SR <: ScaffoldingResult[ScaffoldingState], GR <: GradingResult[GradingState],
  S <: Scaffolder[ScaffoldingState, SR], G <: Grader[EditorState, GradingState, GR]
] {
  def model: FullInteractionExerciseModel[EditorState, ScaffoldingState, GradingState, SR, GR]

  def controller: FullInteractionController[EditorState, ScaffoldingState, GradingState, SR, GR, S, G]

  def visualizer: FullInteractionVisualizer[EditorState, ScaffoldingState, GradingState, SR, GR, S, G]
}
