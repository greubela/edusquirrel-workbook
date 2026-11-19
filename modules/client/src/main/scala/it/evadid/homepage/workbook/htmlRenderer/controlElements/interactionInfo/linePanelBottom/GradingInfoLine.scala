package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.linePanelBottom

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.{InteractionFeedbackView, InteractionHintPanel}
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.homepage.control.info.interaction.GradingReporter

case class GradingInfoLine[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], gradingReporter: GradingReporter) extends HtmlAppElement {
  override def getDomElement(): Element =
    div(cls := "grading-feedback-panel",
      GradingInfoLine.render(new InteractionFeedbackView(interaction, gradingReporter, fullInfo, laminarHelper)),
      InteractionHintPanel(gradingReporter).getDomElement())
}

object GradingInfoLine {
  private[interactionInfo] def render[F <: InteractionFormat](view: InteractionFeedbackView[F]): Element = {
    import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionInfoPanelIcons.*
    val canGrade = view.interaction.gradingElements.nonEmpty
    val canHint = view.interaction.scaffoldingElement.nonEmpty
    val scoreText = view.grading.combineWith(view.label("gradingCompactScore")).map { (state, template) =>
      template.replace("{correct}", state.correct.toString).replace("{total}", state.total.toString)
    }
    val hintText = view.scaffolding.flatMapSwitch(result => view.label(view.scaffoldingLabel(result)))
      .combineWith(view.label("scaffoldingLabel")).map((status, label) => s"$label: $status")
    div(cls := "interaction-feedback grading-info-line",
      div(cls := "feedback-actions",
        Option.when(canGrade)(button(typ := "button", cls := "feedback-action",
          aria.label <-- view.label("gradingRequest"), title <-- view.label("gradingRequest"),
          createGradingUnrequested(), span(cls := "feedback-button-label", child.text <-- view.label("gradingRequest")),
          disabled <-- view.grading.combineWith(view.disabled).map((grading, disabled) => disabled || grading.pending),
          onClick --> (_ => view.requestGrading()))),
        Option.when(canHint)(button(typ := "button", cls := "feedback-action scaffolding-info",
          aria.label <-- view.label("scaffoldingRequest"), title <-- view.label("scaffoldingRequest"),
          createScaffoldingNothing(), span(cls := "feedback-button-label", child.text <-- view.label("scaffoldingRequest")),
          disabled <-- view.scaffolding.combineWith(view.disabled).map((result, disabled) =>
            disabled || result.status == it.evadid.workbook.meta.ScaffoldingReport.Status.Pending),
          dataAttr("tooltip") <-- view.scaffolding.flatMapSwitch(view.scaffoldingText),
          onClick --> (_ => view.requestScaffolding())))
      ),
      table(cls := "feedback-status-table", aria.live := "polite",
        thead(cls := "feedback-sr-only", tr(
          Option.when(canGrade)(th(child.text <-- view.label("gradingInfoLabel"))),
          Option.when(canHint)(th(child.text <-- view.label("scaffoldingLabel"))))),
        tbody(tr(
          Option.when(canGrade)(td(
            strong(title <-- scoreText, aria.label <-- scoreText,
              child.text <-- view.grading.map(state => s"${state.correct}/${state.total} ✓")),
            child.maybe <-- view.grading.combineWith(view.label("gradingCompactUnavailable")).map { (state, template) =>
              Option.when(state.unavailable > 0)(span(cls := "feedback-muted",
                title := template.replace("{count}", state.unavailable.toString),
                aria.label := template.replace("{count}", state.unavailable.toString), s"⚠ ${state.unavailable}"))
            }
          )),
          Option.when(canHint)(td(title <-- hintText, aria.label <-- hintText,
            span(cls := "feedback-hint-status", child.text <-- view.scaffolding.map { result =>
              import it.evadid.workbook.meta.ScaffoldingReport.Status
              result.status match {
                case Status.NotRequested => "—"
                case Status.Pending => "…"
                case Status.Error => "!"
                case Status.Ready => "✓"
              }
            })))
        ))
      )
    )
  }
}
