package workbook.workbookHtmlElements.abstractions

import com.raquo.laminar.api.L
import workbook.model.exercise.ExerciseContent
import workbook.model.feedback.grading.GradingResult
import workbook.model.feedback.scaffolding.ScaffoldingResult
import workbook.model.interaction.{Editor, Grader, Scaffolder}
import workbook.model.interaction.full.{FullInteractionController, FullInteractionModel, FullInteractionVisualizer}
import workbook.model.states.InteractionState

trait HtmlFullInteractionContainer[
  EditorState <: InteractionState, ScaffoldingState <: InteractionState, GradingState <: InteractionState,
  SR <: ScaffoldingResult, GR <: GradingResult,
  E <: Editor[EditorState], S <: Scaffolder[ScaffoldingState, SR], G <: Grader[EditorState, GradingState, GR]
] extends HtmlWorkbookElement {

  def exercise: ExerciseContent

  def model: FullInteractionModel[EditorState, ScaffoldingState, GradingState, SR, GR]

  def controller: FullInteractionController[EditorState, ScaffoldingState, GradingState, SR, GR, E, S, G]

  def componentVisualizer: FullInteractionVisualizer[EditorState, ScaffoldingState, GradingState, SR, GR, E, S, G]

  def notifyOnModelUpdate(): Unit

  //  def setDisplayType(displayType: FullExerciseDisplayType): Unit

  def enableInteraction(): Unit

  def disableInteraction(): Unit
}

