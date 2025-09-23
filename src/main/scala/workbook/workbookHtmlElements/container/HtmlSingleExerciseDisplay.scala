package workbook.workbookHtmlElements.container

import com.raquo.laminar.api.L.*
import contentmanagement.model.language.AppLanguage
import workbook.model.exercise.ExerciseContent
import workbook.workbookHtmlElements.HtmlPlaintextInstructionElement
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement
import workbook.workbookHtmlElements.exercises.HtmlTextBasedGptExercise

case class HtmlSingleExerciseDisplay(exerciseHtmlElement: HtmlWorkbookElement) extends HtmlWorkbookElement {


  private val domElement: Element = {
    div(cls := "container-exercises-display",
      exerciseHtmlElement.getDomElement()
    )
  }


  def getDomElement(): Element = domElement


}
