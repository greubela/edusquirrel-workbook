package workbook.model.interaction

import workbook.model.states.InteractionState
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement


trait Editor[EditorState <: InteractionState] extends HtmlWorkbookElement {
  def loadState(stateToLoad: EditorState): Unit

  def getCurrentState(): EditorState
}
