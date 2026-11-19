package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.editorSidePanel

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.ElementCard
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionFeedbackView
import it.evadid.workbook.meta.GradingSummary
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.FeedbackPresentation.*
import it.evadid.workbook.meta.GradingSummary.Group
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.linePanelBottom.GradingInfoLine
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookDisplayElement, WorkbookInteractionElement}
import it.evadid.homepage.control.info.interaction.GradingReporter
import java.time.format.DateTimeFormatter

case class InteractionInfoSidebar[F <: InteractionFormat](
    underlyingInteraction: WorkbookInteractionElement[F],
    associatedInstruction: Option[WorkbookDisplayElement],
    gradingReporter: GradingReporter
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

  private def groupCount(group: Group): Element = {
    val selected = view.grading.map(_.in(group))
    if group != Group.Correct && group != Group.Incorrect then
      span(cls := "feedback-count", child.text <-- selected.map(_.size.toString))
    else span(cls := "feedback-counts", List(false, true).map { hidden =>
      val count = view.grading.map(state => if hidden then state.hiddenCount(group) else state.visibleCount(group))
      val description = count.combineWith(view.label(if hidden then "gradingHiddenCount" else "gradingVisibleCount"))
        .map((number, text) => text.replace("{count}", number.toString))
      span(cls := "feedback-count-item", title <-- description, aria.label <-- description,
        it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionInfoPanelIcons.createVisibilityIcon(hidden),
        span(aria.hidden := true, child.text <-- count.map(_.toString)))
    })
  }

  private def resultHeader(label: String, icon: String): Element =
    th(title <-- view.label(label), aria.label <-- view.label(label),
      span(cls := "feedback-column-label", child.text <-- view.label(label)),
      span(cls := "feedback-column-icon", aria.hidden := true, icon))

  private def resultTable(entries: Seq[GradingSummary.Entry]): Element =
    div(cls := "feedback-table-scroll",
      table(cls := "feedback-results-table",
        thead(tr(th(child.text <-- view.label("gradingTestDescription")),
          resultHeader("gradingExecutionDate", "◷"), resultHeader("gradingFeedback", "↩"))),
        tbody(entries.map(resultRow))))

  private def groupSection(group: Group): Element =
    detailsTag(cls := s"feedback-group feedback-status--${group.tone}",
      summaryTag(cls := "feedback-summary", strong(child.text <-- view.label(group.labelKey)), groupCount(group)),
      div(cls := "feedback-group-content", child <-- view.grading.map { state =>
        val visible = state.visibleIn(group)
        if visible.nonEmpty then resultTable(visible)
        else p(cls := "feedback-muted", child.text <-- view.label(
          if state.in(group).nonEmpty then "gradingPrivate" else "gradingNoTests"))
      }))

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
      htmlProp("open", com.raquo.laminar.codecs.BooleanAsIsCodec) <-- view.scaffolding.map(_.status != it.evadid.workbook.meta.ScaffoldingReport.Status.NotRequested).distinct,
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
