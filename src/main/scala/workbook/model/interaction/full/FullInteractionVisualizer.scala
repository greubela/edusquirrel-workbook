package workbook.model.interaction.full

import com.raquo.laminar.api.L.Element
import workbook.model.feedback.grading.GradingResult
import workbook.model.feedback.scaffolding.ScaffoldingResult
import workbook.model.interaction.{Editor, Grader, Scaffolder}
import workbook.model.states.InteractionState

trait FullInteractionVisualizer[
  EditorState <: InteractionState, ScaffoldingState <: InteractionState, GradingState <: InteractionState,
  SR <: ScaffoldingResult, GR <: GradingResult,
  E <: Editor[EditorState], S <: Scaffolder[ScaffoldingState, SR], G <: Grader[EditorState, GradingState, GR]
] {

  def visualizeEditor(curState: EditorState): Element

  def visualizeScaffolderStateEditor(curState: ScaffoldingState): Element

  def visualizeScaffoldingResult(curScaffolderState: ScaffoldingState, curResult: SR): Element
  
  def visualizeGraderStateEditor(curState: GradingState): Element

  def visualizeGradingResult(curGradingState: GradingState, curResult: GR): Element

} 