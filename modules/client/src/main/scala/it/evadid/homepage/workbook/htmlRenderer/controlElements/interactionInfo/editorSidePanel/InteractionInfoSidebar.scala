package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.editorSidePanel

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.ElementCard
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.{GradingSummary, InteractionFeedbackView}
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.GradingSummary.Group
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.linePanelBottom.GradingInfoLine
import it.evadid.workbook.abstractions.{WorkbookDisplayElement, WorkbookInteractionElementFull}
import java.time.format.DateTimeFormatter

case class InteractionInfoSidebar[T, GA, SA](
    underlyingInteraction: WorkbookInteractionElementFull[T, GA, SA],
    associatedInstruction: Option[WorkbookDisplayElement],
    gradingReporter: it.evadid.workbook.meta.InteractionVariableGradingReporter[T, GA, SA]
) extends HtmlAppElement {
  private lazy val view = new InteractionFeedbackView(underlyingInteraction, gradingReporter, fullInfo, laminarHelper)
  private val dateFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")

  def createInstructionElement: Option[Element] = associatedInstruction.map { displayElement =>
    val rendered = HtmlRenderFactory.renderWorkbookElement(displayElement).rendering.elementsWithoutContainer
    ElementCard(LanguageMapContentId("basic/SidepanelInstructionTitle"), rendered).getDomElement()
  }

  private def resultRow(entry: GradingSummary.Entry): Element = tr(
    td(span(cls := "feedback-test-description", child.text <-- view.description(entry)),
      Option.when(entry.result.exists(_.gradingStatus == it.evadid.workbook.abstractions.grading.GradingStatus.PartiallyCorrect()))(
        small(cls := "feedback-muted", child.text <-- view.label("gradingPartiallyCorrect")))),
    td(cls := "feedback-date", entry.result.filter(_ => entry.executed).map(_.gradingTime.format(dateFormat)).getOrElse("—")),
    td(cls := "feedback-text", child.text <-- view.feedback(entry))
  )

  private def groupSection(group: Group): Element = {
    val selected = view.grading.map(_.in(group))
    detailsTag(cls := s"feedback-group feedback-status--${group.tone}",
      summaryTag(cls := "feedback-summary",
        strong(child.text <-- view.label(group.labelKey)),
        if group == Group.Correct || group == Group.Incorrect then
          span(cls := "feedback-counts",
            span(child.text <-- selected.combineWith(view.label("gradingVisibleCount")).map((entries, text) =>
              text.replace("{count}", entries.count(!_.hidden).toString))),
            span(cls := "feedback-muted", child.text <-- selected.combineWith(view.label("gradingHiddenCount")).map((entries, text) =>
              text.replace("{count}", entries.count(_.hidden).toString))))
        else span(cls := "feedback-count", child.text <-- selected.map(_.size.toString))
      ),
      div(cls := "feedback-group-content",
        // Filter before constructing any description, feedback or timestamp nodes.
        child <-- view.grading.map { state =>
          val visible = state.visibleIn(group)
          if visible.isEmpty then p(cls := "feedback-muted", child.text <-- view.label(
            if state.in(group).nonEmpty then "gradingPrivate" else "gradingNoTests"))
          else div(cls := "feedback-table-scroll",
            table(cls := "feedback-results-table",
              thead(tr(th(child.text <-- view.label("gradingTestDescription")),
                th(child.text <-- view.label("gradingExecutionDate")), th(child.text <-- view.label("gradingFeedback")))),
              tbody(visible.map(resultRow))))
        })
    )
  }

  def createGradingElement: Option[Element] = Option.when(gradingReporter.isGradingSupported) {
    detailsTag(cls := "element-card feedback-section",
      summaryTag(cls := "collapsible-header feedback-summary",
        h3(cls := "element-card-label", child.text <-- view.label("SidepanelGradingTitle")),
        span(cls := "feedback-score", aria.live := "polite", child.text <-- view.grading.combineWith(view.label("gradingScore")).map {
          (state, text) => text.replace("{score}", state.score)
        })),
      div(cls := "feedback-section-content", Group.values.toList.map(groupSection)))
  }

  def createScaffoldingElement: Option[Element] = Option.when(gradingReporter.isScaffoldingSupported) {
    detailsTag(cls := "element-card feedback-section",
      summaryTag(cls := "collapsible-header feedback-summary",
        h3(cls := "element-card-label", child.text <-- view.label("SidepanelScaffoldingTitle")),
        span(cls := "feedback-muted", child.text <-- view.scaffolding.flatMapSwitch(result => view.label(view.scaffoldingLabel(result))))),
      p(cls := "feedback-text feedback-section-content", child.text <-- view.scaffolding.flatMapSwitch(view.scaffoldingText)))
  }

  override def getDomElement(): Element = div(cls := "side-panel-interaction interaction-feedback",
    createInstructionElement,
    Option.when(gradingReporter.isGradingSupported || gradingReporter.isScaffoldingSupported)(GradingInfoLine.render(view)),
    createGradingElement,
    createScaffoldingElement)
}
