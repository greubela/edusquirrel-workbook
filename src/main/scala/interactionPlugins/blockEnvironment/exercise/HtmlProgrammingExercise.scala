package interactionPlugins.blockEnvironment.exercise

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import interactionPlugins.blockEnvironment.programming.BeProgram
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement
import workbook.workbookHtmlElements.{HtmlExerciseTitleElement, HtmlPlaintextInstructionElement}

case class HtmlProgrammingExercise(exerciseContent: ProgrammingExercise) extends HtmlWorkbookElement {


  val htmlTitleElement: HtmlExerciseTitleElement = HtmlExerciseTitleElement(exerciseContent.titleMap)
  val htmlInstructionElement: HtmlPlaintextInstructionElement = HtmlPlaintextInstructionElement(exerciseContent.instructionMap)

  private val currentProgram: Var[BeProgram] = Var(BeProgram(BeProgram.miniProgramExpression()))

  private val domElement: Element = div(cls := "container-exercise style-vbox",

    htmlTitleElement.getDomElement(),
    htmlInstructionElement.getDomElement(),

    // ToDo: two columns. 
    // left column: pre-view of the currentProgramm using HtmlTreeDispay
    // right column: SVG Preview of the expected resul ( a pentagon)
    // bottom line (full width): a button that opens a full editor via HtmlFullScreenElement in mainApp
  )

  override def getDomElement(): L.Element = domElement


}
