package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.editorSidePanel

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.ElementCard
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.{FeedbackPanels, InteractionFeedbackView}
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookDisplayElement, WorkbookInteractionElement}
import it.evadid.homepage.control.info.interaction.GradingReporter

case class InteractionInfoSidebar[F <: InteractionFormat](
    underlyingInteraction: WorkbookInteractionElement[F],
    associatedInstruction: Option[WorkbookDisplayElement],
    gradingReporter: GradingReporter
) extends HtmlAppElement {
  private lazy val view = new InteractionFeedbackView(underlyingInteraction, gradingReporter, fullInfo, laminarHelper)

  def createInstructionElement: Option[Element] = associatedInstruction.map { displayElement =>
    val rendered = HtmlRenderFactory.renderWorkbookElement(displayElement).rendering.elementsWithoutContainer
    ElementCard(LanguageMapContentId("basic/SidepanelInstructionTitle"), rendered).getDomElement()
  }

  override def getDomElement(): Element = div(cls := "side-panel-interaction",
    createInstructionElement,
    Option.when(gradingReporter.isGradingSupported || gradingReporter.isScaffoldingSupported)(
      div(cls := "feedback-panel feedback-panel--sidebar", FeedbackPanels.panel(view, FeedbackPanels.Placement.Sidebar))))
}
