package interactionPlugins.blockEnvironment.programming.editor

import com.raquo.laminar.api.L.{*, given}
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

class HtmlFullscreenEditorElement extends HtmlWorkbookElement {

  private def placeholderPanel(areaClass: String, label: String): Element =
    div(
      cls := s"be-fullscreen-panel $areaClass",
      span(
        cls := "be-fullscreen-panel__placeholder",
        label
      )
    )

  private val rootElement: Element =
    div(
      cls := "be-fullscreen-editor",
      placeholderPanel("be-fullscreen-editor__header", "Editor Toolbar"),
      placeholderPanel("be-fullscreen-editor__palette", "Block Library"),
      placeholderPanel("be-fullscreen-editor__workspace", "Workspace"),
      placeholderPanel("be-fullscreen-editor__inspector", "Inspector"),
      placeholderPanel("be-fullscreen-editor__console", "Console Output")
    )

  override def getDomElement(): Element = rootElement
}
