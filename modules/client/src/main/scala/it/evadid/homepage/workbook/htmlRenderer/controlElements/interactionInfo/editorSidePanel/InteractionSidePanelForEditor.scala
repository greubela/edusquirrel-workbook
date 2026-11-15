package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.editorSidePanel

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionInfoPanel
import it.evadid.workbook.abstractions.WorkbookInteractionElementFull
import it.evadid.workbook.meta.InteractionVariableGradingReporter

case class InteractionSidePanelForEditor[T, GA, SA](
                                                     underlyingInteraction: WorkbookInteractionElementFull[T, GA, SA],
                                                     gradingReporter: InteractionVariableGradingReporter[T, GA, SA]
                                                   ) extends HtmlAppElement {




  override def getDomElement(): L.Element = div(cls := "interaction-editor__side-panel",
    InteractionInfoPanel(underlyingInteraction, gradingReporter).getDomElement())
}
