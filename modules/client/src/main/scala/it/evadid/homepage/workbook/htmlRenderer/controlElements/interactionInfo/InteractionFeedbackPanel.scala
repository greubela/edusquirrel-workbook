package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.FeedbackPanels.Placement
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.homepage.control.info.interaction.GradingReporter

/** Feedback below an exercise in the workbook overview; the editor sidebar renders the same parts. */
case class InteractionFeedbackPanel[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], gradingReporter: GradingReporter) extends HtmlAppElement {
  override def getDomElement(): Element =
    div(cls := "feedback-panel feedback-panel--overview",
      FeedbackPanels.panel(new InteractionFeedbackView(interaction, gradingReporter, fullInfo, laminarHelper), Placement.Overview))
}
