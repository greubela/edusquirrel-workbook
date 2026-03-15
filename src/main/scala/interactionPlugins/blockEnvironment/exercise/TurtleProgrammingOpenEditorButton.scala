package interactionPlugins.blockEnvironment.exercise

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import interactionPlugins.blockEnvironment.config.BlockEnvironmentLanguageMap
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.editor.HtmlFullscreenTurtleEditorElement
import interactionPlugins.blockEnvironment.programming.editor.elements.EditorState
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

case class TurtleProgrammingOpenEditorButton(workbookInfoVar: Var[WorkbookInfo], editorState: EditorState) extends HtmlWorkbookElement {


  private val fullscreenEditor = HtmlFullscreenTurtleEditorElement(editorState)

  private def openFullEditor(): Unit = {
    //fullscreenEditor.bindToProgram(currentProgram)
    workbookInfoVar.now().fullscreenElement.setElementFullscreen(fullscreenEditor.getDomElement())
  }

  override def getDomElement(): Element =
    button(
      typ := "button",
      cls := "programming-exercise-action-button",
      child <-- workbookInfoVar.signal.map(_.config.currentWorkbookLanguage).map(BlockEnvironmentLanguageMap.languageMapOpenEditor.getInLanguage),
      onClick --> (_ => openFullEditor())
    )
  
}
