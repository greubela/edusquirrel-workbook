package interactionPlugins.blockEnvironment.exercise

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import interactionPlugins.blockEnvironment.config.BlockEnvironmentLanguageMap
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.editor.HtmlFullscreenTurtleEditorElement
import interactionPlugins.blockEnvironment.programming.editor.elements.EditorState
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.{AllWorkbookInfo, WorkbookInfo}

case class TurtleProgrammingOpenEditorButton(workbookInfo: AllWorkbookInfo, editorState: EditorState) extends HtmlWorkbookElement {


  private val fullscreenEditor = HtmlFullscreenTurtleEditorElement(editorState)

  private def openFullEditor(): Unit = {
    //fullscreenEditor.bindToProgram(currentProgram)
    workbookInfo.technicalElements.fullScreenContainer.setElementFullscreen(fullscreenEditor.getDomElement())
  }

  override def getDomElement(): Element =
    button(
      typ := "button",
      cls := "programming-exercise-action-button",
      child <-- workbookInfoVar.signal.map(_.config.currentWorkbookLanguage).map(BlockEnvironmentLanguageMap.languageMapOpenEditor.getInLanguage),
      onClick --> (_ => openFullEditor())
    )
  
}
