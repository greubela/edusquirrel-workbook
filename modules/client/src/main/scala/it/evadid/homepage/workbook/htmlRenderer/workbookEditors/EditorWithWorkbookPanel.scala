package it.evadid.homepage.workbook.htmlRenderer.workbookEditors

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.abstractions.{SimpleWebEditor, WebEditorConfig}
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionInfoPanel
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.editorSidePanel.InteractionSidePanelForEditor
import it.evadid.workbook.abstractions.WorkbookInteractionElementFull
import it.evadid.workbook.meta.InteractionVariableGradingReporter

case class EditorWithWorkbookPanel[T, GA, SA, EC <: WebEditorConfig]
(
  interaction: WorkbookInteractionElementFull[T, GA, SA],
  gradingReporter: InteractionVariableGradingReporter[T, GA, SA],
  additionalModules: DomElementCollection,
  editor: SimpleWebEditor[T, EC]
) extends HtmlAppElement {

  lazy val sidePanel = InteractionSidePanelForEditor[T, GA, SA](interaction, gradingReporter)

  override def getDomElement(): L.Element = div(cls := "interaction-editor",
    InteractionSidePanelForEditor(interaction, gradingReporter).getDomElement(),
    //InteractionInfoPanel(interaction, gradingReporter).getDomElement(),
    div(cls := "interaction-editor__content", editor.getDomElement())
  )

  override def dismissOnOutsideClick: Boolean = editor.dismissOnOutsideClick

}
