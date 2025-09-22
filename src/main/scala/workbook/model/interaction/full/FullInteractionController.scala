package workbook.model.interaction.full

import workbook.model.feedback.grading.GradingResult
import workbook.model.feedback.scaffolding.ScaffoldingResult
import workbook.model.interaction.{Editor, Grader, Scaffolder}
import workbook.model.states.InteractionState

case class FullInteractionController[
  EditorState <: InteractionState, ScaffoldingState <: InteractionState, GradingState <: InteractionState,
  SR <: ScaffoldingResult, GR <: GradingResult,
  E <: Editor[EditorState], S <: Scaffolder[ScaffoldingState, SR], G <: Grader[EditorState, GradingState, GR]
] (editor: E, scaffolder: S, grader: G) {

}