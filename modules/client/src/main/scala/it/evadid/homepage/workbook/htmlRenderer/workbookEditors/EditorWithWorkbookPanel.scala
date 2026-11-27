package it.evadid.homepage.workbook.htmlRenderer.workbookEditors

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.abstractions.{SimpleWebEditor, WebEditorConfig}
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.ElementCard
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionInfoPanel
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.editorSidePanel.InteractionSidePanelForEditor
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookDisplayElement, WorkbookInteractionElement}
import it.evadid.homepage.control.info.interaction.GradingReporter

case class EditorWithWorkbookPanel[F <: InteractionFormat, EC <: WebEditorConfig](
  fullInfo: FullInfo,
  interaction: WorkbookInteractionElement[F],
  associatedInstruction: Option[WorkbookDisplayElement],
  gradingReporter: GradingReporter,
  additionalModules: Seq[ElementCard],
  editor: SimpleWebEditor[InteractionFormat.State[F], EC]
) extends HtmlAppElement {

  lazy val sidePanel = InteractionSidePanelForEditor(fullInfo, interaction, associatedInstruction, gradingReporter, additionalModules)

  override def getDomElement(): L.Element =
    div(cls := "editor-with-panel",
      div(cls := "fullscreen-editor", editor.getDomElement()),
      sidePanel.getDomElement(),
    )

  override def dismissOnOutsideClick: Boolean = editor.dismissOnOutsideClick

}
