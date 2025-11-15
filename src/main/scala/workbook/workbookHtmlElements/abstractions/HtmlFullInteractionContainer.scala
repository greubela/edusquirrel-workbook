package workbook.workbookHtmlElements.abstractions

import com.raquo.airstream.state.Var
import contentmanagement.model.language.{AppLanguage, LanguageMap}
import workbook.model.display.InteractionDisplayState
import workbook.model.feedback.grading.GradingResult
import workbook.model.feedback.scaffolding.ScaffoldingResult
import workbook.model.interaction.full.HtmlFullInteractionModel
import workbook.model.interaction.{Grader, Scaffolder}
import workbook.model.states.InteractionState
import workbook.workbookHtmlElements.abstractions.HtmlFullInteractionContainer.*
import workbook.workbookHtmlElements.abstractions.HtmlFullInteractionExercise.ExerciseStyle
import workbook.workbookHtmlElements.helper.HtmlHelper

trait HtmlFullInteractionContainer[
  EditorState <: InteractionState, ScaffoldingState <: InteractionState, GradingState <: InteractionState,
  SR <: ScaffoldingResult[ScaffoldingState], GR <: GradingResult[GradingState],
  S <: Scaffolder[ScaffoldingState, SR], G <: Grader[EditorState, GradingState, GR]
] extends HtmlWorkbookElement {

  def correspondingExercise: HtmlFullInteractionExercise[EditorState, ScaffoldingState, GradingState, SR, GR,  S, G]

  def interactionModel: HtmlFullInteractionModel[EditorState, ScaffoldingState, GradingState, SR, GR,  S, G]

  def displayState: Var[InteractionDisplayState]

}

object HtmlFullInteractionContainer {




}

