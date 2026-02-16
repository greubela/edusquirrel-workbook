package workbook.workbookHtmlElements.abstractions
/*
import com.raquo.laminar.api.L.Element
import workbook.model.exercise.ExerciseContent
import workbook.model.feedback.grading.GradingResult
import workbook.model.feedback.scaffolding.ScaffoldingResult
import workbook.model.interactionOld.full.HtmlFullInteractionModel
import workbook.model.interactionOld.{Grader, Scaffolder}
import workbook.model.states.InteractionState
import workbook.workbookHtmlElements.abstractions.HtmlFullInteractionExercise.ExerciseStyle.*
import workbook.workbookHtmlElements.abstractions.HtmlFullInteractionExercise.{ExerciseStyle, knownCssExerciseClasses}
import workbook.workbookHtmlElements.helper.HtmlHelper

// represents a SINGLE (sub-) exercise. There might be more in the same container of one or multiple exercises
trait HtmlFullInteractionExercise[
  EditorState <: InteractionState, ScaffoldingState <: InteractionState, GradingState <: InteractionState,
  SR <: ScaffoldingResult[ScaffoldingState], GR <: GradingResult[GradingState],
  S <: Scaffolder[ScaffoldingState, SR], G <: Grader[EditorState, GradingState, GR]
] extends HtmlWorkbookElement {

  def exerciseContent: ExerciseContent

  def htmlTitleElement: HtmlWorkbookElement

  def htmlInstructionElement: HtmlWorkbookElement

  def htmlInteractionModel: HtmlFullInteractionModel[EditorState, ScaffoldingState, GradingState, SR, GR, S, G]

  def htmlInteractionContainer: HtmlFullInteractionContainer[EditorState, ScaffoldingState, GradingState, SR, GR, S, G]

  def getDomElement(): Element

  def setStyle(style: ExerciseStyle): Unit = {
    if (getDomElement() != null) {
      HtmlHelper.ensureOneStyleFromListSet(getDomElement(), knownCssExerciseClasses, style)
    }
  }
}

object HtmlFullInteractionExercise {


  private val knownCssExerciseClasses: Seq[ExerciseStyle] = List(Style_Default, Style_Fullscreen, Style_Compact)


  enum ExerciseStyle(val correspondingClassString: String) extends CssSwitchableClass {
    case Style_Default extends ExerciseStyle("style-vbox")
    case Style_Fullscreen extends ExerciseStyle("style-maximized")
    case Style_Compact extends ExerciseStyle("style-compact")
  }
  

}*/