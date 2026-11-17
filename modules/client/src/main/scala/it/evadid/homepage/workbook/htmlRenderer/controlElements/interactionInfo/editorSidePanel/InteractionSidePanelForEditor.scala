package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.editorSidePanel

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.ElementCard
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookDisplayElement, WorkbookInteractionElement}
import it.evadid.workbook.meta.InteractionVariableGradingReporter

case class InteractionSidePanelForEditor[F <: InteractionFormat](
                                                                      underlyingInteraction: WorkbookInteractionElement[F],
                                                                      associatedInstruction: Option[WorkbookDisplayElement],
                                                                      gradingReporter: InteractionVariableGradingReporter[F],
                                                                      additionalModules: List[ElementCard]
                                                                    ) extends HtmlAppElement {

  override def getDomElement(): L.Element = {
    div(cls := "fullscreen-sidepanel",
      div(cls := "side-panel-modules", additionalModules.map(_.getDomElement())),
      InteractionInfoSidebar(underlyingInteraction, associatedInstruction, gradingReporter).getDomElement()
    )
  }
}
