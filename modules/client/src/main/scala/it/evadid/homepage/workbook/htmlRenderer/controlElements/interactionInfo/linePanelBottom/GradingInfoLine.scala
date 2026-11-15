package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.linePanelBottom

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionFeedbackView
import it.evadid.workbook.abstractions.WorkbookInteractionUntyped
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingStatus.ScaffoldingRequestedButNotFinished
import it.evadid.workbook.meta.InteractionVariableGradingReporter

case class GradingInfoLine(interaction: WorkbookInteractionUntyped, gradingReporter: InteractionVariableGradingReporter[?, ?, ?]) extends HtmlAppElement {
  override def getDomElement(): Element =
    GradingInfoLine.render(new InteractionFeedbackView(interaction, gradingReporter, fullInfo, laminarHelper))
}

object GradingInfoLine {
  private[interactionInfo] def render(view: InteractionFeedbackView): Element = {
    val canGrade = view.interaction.gradingElements.nonEmpty
    val canHint = view.interaction.scaffoldingElement.nonEmpty
    div(cls := "interaction-feedback grading-info-line",
      div(cls := "feedback-actions",
        Option.when(canGrade)(button(typ := "button", cls := "feedback-action",
          child.text <-- view.label("gradingRequest"),
          disabled <-- view.grading.combineWith(view.disabled).map((grading, disabled) => disabled || grading.pending),
          onClick --> (_ => view.requestGrading()))),
        Option.when(canHint)(button(typ := "button", cls := "feedback-action scaffolding-info",
          child.text <-- view.label("scaffoldingRequest"),
          disabled <-- view.scaffolding.combineWith(view.disabled).map((result, disabled) =>
            disabled || result.exists(_.scaffoldingStatus.isInstanceOf[ScaffoldingRequestedButNotFinished])),
          dataAttr("tooltip") <-- view.scaffolding.flatMapSwitch(view.scaffoldingText),
          onClick --> (_ => view.requestScaffolding())))
      ),
      table(cls := "feedback-status-table", aria.live := "polite",
        tbody(tr(
          Option.when(canGrade)(List(
            th(child.text <-- view.label("gradingInfoLabel")),
            td(
              strong(child.text <-- view.grading.combineWith(view.label("gradingCompactScore")).map { (state, template) =>
                template.replace("{correct}", state.correct.toString).replace("{total}", state.total.toString)
              }),
              child.maybe <-- view.grading.combineWith(view.label("gradingCompactUnavailable")).map { (state, template) =>
                Option.when(state.unavailable > 0)(span(cls := "feedback-muted",
                  template.replace("{count}", state.unavailable.toString)))
              }
            ))),
          Option.when(canHint)(List(
            th(child.text <-- view.label("scaffoldingLabel")),
            td(child.text <-- view.scaffolding.flatMapSwitch(result => view.label(view.scaffoldingLabel(result))))))
        ))
      )
    )
  }
}
