package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.editorSidePanel

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.ElementCard
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookDisplayElement, WorkbookInteractionElement}
import it.evadid.homepage.control.info.interaction.GradingReporter

/** Side panel of a fullscreen editor: the task on top (collapsible, as it may be long), then the editor's modules,
 * then the interaction's feedback. */
case class InteractionSidePanelForEditor[F <: InteractionFormat](
                                                                      underlyingInteraction: WorkbookInteractionElement[F],
                                                                      associatedInstruction: Option[WorkbookDisplayElement],
                                                                      gradingReporter: GradingReporter,
                                                                      additionalModules: Seq[ElementCard]
                                                                    ) extends HtmlAppElement {

  private def instructionCard: Option[Element] = associatedInstruction.map { displayElement =>
    val rendered = HtmlRenderFactory.renderWorkbookElement(displayElement).rendering.elementsWithoutContainer
    ElementCard(LanguageMapContentId("basic/SidepanelInstructionTitle"), rendered, collapsible = true).getDomElement()
  }

  override def getDomElement(): L.Element = {
    div(cls := "fullscreen-sidepanel",
      instructionCard.map(card => div(cls := "side-panel-instruction", card)),
      div(cls := "side-panel-modules", additionalModules.map(_.getDomElement())),
      InteractionInfoSidebar(underlyingInteraction, gradingReporter).getDomElement()
    )
  }
}
