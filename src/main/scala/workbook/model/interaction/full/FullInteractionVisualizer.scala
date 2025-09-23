package workbook.model.interaction.full

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.{Element, Signal}
import workbook.model.display.InteractionComponent.*
import workbook.model.feedback.grading.GradingResult
import workbook.model.feedback.scaffolding.ScaffoldingResult
import workbook.model.interaction.{Grader, Scaffolder}
import workbook.model.states.InteractionState

trait FullInteractionVisualizer[
  EditorState <: InteractionState, ScaffoldingState <: InteractionState, GradingState <: InteractionState,
  SR <: ScaffoldingResult[ScaffoldingState], GR <: GradingResult[GradingState],
  S <: Scaffolder[ScaffoldingState, SR], G <: Grader[EditorState, GradingState, GR]
] {

  def visualizeEditor(data: Var[EditorState]): InteractionComponentForRole

  def visualizeScaffolderStateEditor(data: Var[ScaffoldingState]): InteractionComponentForRole

  def visualizeGraderStateEditor(data: Var[GradingState]): InteractionComponentForRole

  def visualizeScaffoldingResult(data: Var[Option[SR]]): InteractionComponentForRole

  def visualizeGradingResult(data: Var[Option[GR]]): InteractionComponentForRole

}