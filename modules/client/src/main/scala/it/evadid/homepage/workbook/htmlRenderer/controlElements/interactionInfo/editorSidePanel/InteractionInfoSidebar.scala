package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.editorSidePanel

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.{FeedbackPanels, InteractionFeedbackView}
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.homepage.control.info.interaction.GradingReporter

/** Feedback of the interaction edited in a fullscreen editor; the task is shown by [[InteractionSidePanelForEditor]]. */
case class InteractionInfoSidebar[F <: InteractionFormat](
    underlyingInteraction: WorkbookInteractionElement[F],
    gradingReporter: GradingReporter
) extends HtmlAppElement {
  private lazy val view = new InteractionFeedbackView(underlyingInteraction, gradingReporter, fullInfo, laminarHelper)

  override def getDomElement(): Element = div(cls := "side-panel-interaction",
    Option.when(gradingReporter.isGradingSupported || gradingReporter.isScaffoldingSupported)(
      div(cls := "feedback-panel feedback-panel--sidebar", FeedbackPanels.panel(view, FeedbackPanels.Placement.Sidebar))))
}
