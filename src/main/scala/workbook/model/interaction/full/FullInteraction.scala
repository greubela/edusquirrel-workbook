package workbook.model.interaction.full

import workbook.model.feedback.grading.GradingResult
import workbook.model.feedback.scaffolding.ScaffoldingResult
import workbook.model.interaction.{Editor, Grader, Scaffolder}
import workbook.model.states.InteractionState

trait FullInteraction[
  EditorState <: InteractionState, ScaffoldingState <: InteractionState, GradingState <: InteractionState,
  SR <: ScaffoldingResult, GR <: GradingResult,
  E <: Editor[EditorState], S <: Scaffolder[ScaffoldingState, SR], G <: Grader[EditorState, GradingState, GR]
] {
  def model: FullInteractionModel[EditorState, ScaffoldingState, GradingState, SR, GR]

  def controller: FullInteractionController[EditorState, ScaffoldingState, GradingState, SR, GR, E, S, G]

  def visualizer: FullInteractionVisualizer[EditorState, ScaffoldingState, GradingState, SR, GR, E, S, G]
}
