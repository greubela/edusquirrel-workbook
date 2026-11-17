package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.editorSidePanel

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.ElementCard
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.{GradingSummary, InteractionFeedbackView}
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.GradingSummary.Group
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.linePanelBottom.GradingInfoLine
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookDisplayElement, WorkbookInteractionElement}
import it.evadid.workbook.meta.InteractionVariableGradingReporter
import java.time.format.DateTimeFormatter

case class InteractionInfoSidebar[F <: InteractionFormat](
    underlyingInteraction: WorkbookInteractionElement[F],
    associatedInstruction: Option[WorkbookDisplayElement],
    gradingReporter: InteractionVariableGradingReporter[F]
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
          span(cls := "feedback-counts", List(false, true).map { hidden =>
            val count = selected.map(_.count(_.hidden == hidden))
            val description = count.combineWith(view.label(if hidden then "gradingHiddenCount" else "gradingVisibleCount"))
              .map((number, text) => text.replace("{count}", number.toString))
            span(cls := "feedback-count-item", title <-- description, aria.label <-- description,
              it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionInfoPanelIcons.createVisibilityIcon(hidden),
              span(aria.hidden := true, child.text <-- count.map(_.toString)))
          })
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
                th(title <-- view.label("gradingExecutionDate"), aria.label <-- view.label("gradingExecutionDate"),
                  span(cls := "feedback-column-label", child.text <-- view.label("gradingExecutionDate")), span(cls := "feedback-column-icon", aria.hidden := true, "◷"))),
                th(title <-- view.label("gradingFeedback"), aria.label <-- view.label("gradingFeedback"),
                  span(cls := "feedback-column-label", child.text <-- view.label("gradingFeedback")), span(cls := "feedback-column-icon", aria.hidden := true, "↩")))),
              tbody(visible.map(resultRow))))))
    )
  }

  def createGradingElement: Option[Element] = Option.when(gradingReporter.isGradingSupported) {
    detailsTag(cls := "element-card feedback-section",
      summaryTag(cls := "collapsible-header feedback-summary",
        h3(cls := "element-card-label", child.text <-- view.label("SidepanelGradingTitle")),
        span(cls := "feedback-score", aria.live := "polite",
          title <-- view.grading.combineWith(view.label("gradingScore")).map((state, text) => text.replace("{score}", state.score)),
          aria.label <-- view.grading.combineWith(view.label("gradingScore")).map((state, text) => text.replace("{score}", state.score)),
          child.text <-- view.grading.map(state => s"Σ ${state.score}"))),
      div(cls := "feedback-section-content", Group.values.toList.map(groupSection)))
  }

  def createScaffoldingElement: Option[Element] = Option.when(gradingReporter.isScaffoldingSupported) {
    detailsTag(cls := "element-card feedback-section",
      htmlProp("open", com.raquo.laminar.codecs.BooleanAsIsCodec) <-- view.scaffolding.map(_.nonEmpty).distinct,
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
