package interactionPlugins.gpt

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import contentmanagement.webElements.genericHtmlElements.editor.SimpleStringExerciseVariableTextEditor
import interactionPlugins.gpt.HtmlTextBasedGptExercise.{gradingButtonSvg, scaffoldingButtonSvg}
import org.scalajs.dom.SVGLinearGradientElement
import workbook.model.exercise.{ExerciseContent, ExerciseVariable}
import workbook.model.history.ExerciseInteractionHistory
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement
import workbook.workbookHtmlElements.basic.{HtmlButtonElement, HtmlExerciseTitleElement, HtmlPlaintextInstructionElement}
import workbook.workbookHtmlElements.container.HtmlFullScreenElement

case class HtmlTextBasedGptExercise(
                                     exerciseContent: TextBasedGptExercise,
                                     fullscreenElement: HtmlFullScreenElement
                                   ) extends
  HtmlWorkbookElement {

  val solutionHistory: ExerciseInteractionHistory[String] = new ExerciseInteractionHistory[String] {
    override def underlyingExercise: ExerciseContent = exerciseContent
    override def editorStateVariable: ExerciseVariable[String] = ExerciseVariable.stringVariable(underlyingExercise, "")
  }
  private val solutionEditor = SimpleStringExerciseVariableTextEditor(solutionHistory.editorStateVariable)

  private val htmlTitleElement = HtmlExerciseTitleElement(exerciseContent.titleMap)
  private val htmlInstructionElement = HtmlPlaintextInstructionElement(exerciseContent.instructionMap)

  private val scaffoldingButton = HtmlButtonElement(scaffoldingButtonSvg, event => println("scaffolding not implemented yet :( "))
  private val submitButton = HtmlButtonElement(gradingButtonSvg, event => println("grading not implemented yet :( "))


  private val domElement: Element = div(cls := "container-exercise style-vbox",

    htmlTitleElement.getDomElement(),
    htmlInstructionElement.getDomElement(),
    solutionEditor.getDomElement(),
    div(
      cls := "button-line",
      scaffoldingButton.getDomElement(),
      submitButton.getDomElement()
    )

  )

  override def getDomElement(): L.Element = domElement


}


object HtmlTextBasedGptExercise {

  val scaffoldingButtonSvg: Element = {
    svg.svg(
      svg.cls := "button-show-scaffolder",
      svg.viewBox := "0 0 24 24",
      svg.path(
        svg.cls := "button-fill",
        svg.d := "M21 12C21 16.9706 16.9706 21 12 21C7.02944 21 3 16.9706 3 12C3 7.02944 7.02944 3 12 3C16.9706 3 21 7.02944 21 12Z"
        //svg.d := "M17 9A5 5 0 0 0 7 9a1 1 0 0 0 2 0 3 3 0 1 1 3 3 1 1 0 0 0-1 1v2a1 1 0 0 0 2 0v-1.1A5 5 0 0 0 17 9z"
      ),
      svg.path(
        svg.d := "M10.5 8.67709C10.8665 8.26188 11.4027 8 12 8C13.1046 8 14 8.89543 14 10C14 10.9337 13.3601 11.718 12.4949 11.9383C12.2273 12.0064 12 12.2239 12 12.5V12.5V13",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"),
      svg.path(
        svg.d := "M12 16H12.01",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round")
    )
  }


  private def createGradingGradient(id: String): ReactiveSvgElement[SVGLinearGradientElement] =
    svg.linearGradient(
      svg.idAttr := id,
      svg.x1 := "4",
      svg.x2 := "20",
      svg.y1 := "0",
      svg.y2 := "0",
      svg.gradientUnits := "userSpaceOnUse",
      svg.stop(
        svg.offsetAttr := "0",
        svg.stopColor := "#00ff00",
      ),
      svg.stop(
        svg.offsetAttr := "0.5",
        svg.stopColor := "#ffff00",
      ),
      svg.stop(
        svg.offsetAttr := "1",
        svg.stopColor := "#ff0000",
      )
    )

  val gradingButtonSvg: Element = {
    svg.svg(
      createGradingGradient("gradient-fill-show"),
      svg.cls := "button-grading button-grading-start",
      svg.viewBox := "0 0 24 24",
      svg.fill := "none",
      svg.path(
        svg.cls := "button-borderpath",
        svg.d := "M3 12C3 4.5885 4.5885 3 12 3C19.4115 3 21 4.5885 21 12C21 19.4115 19.4115 21 12 21C4.5885 21 3 19.4115 3 12Z"
      ),
      svg.path(
        svg.d := "M12 8L12 16",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"
      )
      ,
      svg.path(
        svg.d := "M15 11L12.087 8.08704V8.08704C12.039 8.03897 11.961 8.03897 11.913 8.08704V8.08704L9 11",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"
      )
    )
  }


}