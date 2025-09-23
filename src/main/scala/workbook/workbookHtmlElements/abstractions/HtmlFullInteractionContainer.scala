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
  /*
  def setPresentation(style: ExerciseStyle, layout: InteractionContainerLayouts, highlight: InteractionContainerHighlight): Unit = {
    println("Set Presentation of Exercise " + correspondingExercise.exerciseContent.id + " to " + style + ", " + layout + ", " + highlight)
    correspondingExercise.setStyle(style)
    HtmlHelper.ensureOneStyleFromListSet(getDomElement(), knownCssLayoutClasses, layout)
    setHighlight(highlight)
  }

  def setHighlight(highlight: InteractionContainerHighlight): Unit = {
    println("Set Highlight to " + highlight)
    HtmlHelper.ensureOneStyleFromListSet(getDomElement(), knownCssHighlightClasses, highlight)
  }*/

}

object HtmlFullInteractionContainer {


/*
  private val knownCssHighlightClasses: Seq[InteractionContainerHighlight] = List(
    InteractionContainerHighlight.Nothing, InteractionContainerHighlight.Scaffolder, InteractionContainerHighlight.Editor, InteractionContainerHighlight.Grader)

  private val knownCssLayoutClasses: Seq[InteractionContainerLayouts] = List(
    InteractionContainerLayouts.Layout_Scaffolding,
    InteractionContainerLayouts.Layout_Editor,
    InteractionContainerLayouts.Layout_Grader,
    InteractionContainerLayouts.Layout_Everything)




  enum InteractionContainerLayouts extends CssSwitchableClass {
    case Layout_Scaffolding_Row
    case
  }

  enum InteractionContainerHighlight(val correspondingClassString: String) extends CssSwitchableClass {
    case Nothing extends InteractionContainerHighlight("highlight-nothing")
    case Scaffolder extends InteractionContainerHighlight("highlight-scaffolding")
    case Editor extends InteractionContainerHighlight("highlight-editor")
    case Grader extends InteractionContainerHighlight("highlight-grading")
  }
  */





}

