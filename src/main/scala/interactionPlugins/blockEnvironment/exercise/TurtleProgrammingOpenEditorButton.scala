package interactionPlugins.blockEnvironment.exercise

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.editor.HtmlFullscreenTurtleEditorElement
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

case class TurtleProgrammingOpenEditorButton(workbookInfoVar: Var[WorkbookInfo], currentProgram: Var[BeProgram]) extends HtmlWorkbookElement {


  private val fullscreenEditor = HtmlFullscreenTurtleEditorElement(currentProgram)

  private def openFullEditor(): Unit = {
    //fullscreenEditor.bindToProgram(currentProgram)
    workbookInfoVar.now().fullscreenElement.setElementFullscreen(fullscreenEditor.getDomElement())
  }

  override def getDomElement(): Element =
    button(
      typ := "button",
      cls := "programming-exercise-action-button",
      "Open full editor",
      onClick --> (_ => openFullEditor())
    )
  
}
