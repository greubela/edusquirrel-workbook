package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.{child, div, *}
import org.scalajs.dom.{HTMLDivElement, MouseEvent}
import com.raquo.laminar.nodes.{ReactiveHtmlElement, ReactiveSvgElement}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionInfoPanel.interactionInfoCssString
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookDisplayElement, WorkbookInteractionElement}
import it.evadid.homepage.control.info.interaction.GradingReporter

case class InteractionInfoPanel[F <: InteractionFormat](
                                 fullInfo: FullInfo,
                                 workbookInteractionElement: WorkbookInteractionElement[F],
                                 gradingReporter: GradingReporter)
  extends HtmlAppElement {

  val iconGradingScaffolding = IconGradingAndScaffolding(fullInfo, laminarHelper(fullInfo), workbookInteractionElement, gradingReporter)
  val iconSync = IconSync(fullInfo, laminarHelper(fullInfo), workbookInteractionElement)

  val classSignalCss: Signal[String] = gradingReporter.report.map { report =>
    if report.grading.status.isCorrect then
      interactionInfoCssString + (if report.grading.reliesOnLlm then " status-correct-ai" else " status-correct")
    else if report.interacted then interactionInfoCssString + " status-interacted"
    else interactionInfoCssString
  }


  lazy val domElement: Element = {
    div(cls <-- classSignalCss,
      div(cls := "interaction-info__icons",
        div(cls := "grading-report-icon", child <-- gradingReporter.summary.map(iconGradingScaffolding.deriveGradingSymbol)),
        div(cls := "scaffolding-report-icon", child <-- gradingReporter.scaffolding.map(iconGradingScaffolding.deriveScaffoldingSymbol)),
        div(cls := "sync-report-icon", child <-- iconSync.syncReport.map(iconSync.deriveSyncSymbol)),
        child.maybe <-- gradingReporter.report.map(report =>
          Option.when(report.checks.tracked && report.grading.status.isCorrect)(attemptsTracker(report.checks.failedChecks + 1))),
      )
    )
  }

  /** Shown on the green strip of a solved exercise: the checked answers up to the correct one. */
  private def attemptsTracker(count: Int): Element = {
    def label(key: String) = laminarHelper(fullInfo).plaintextStringSignal(LanguageMapContentId("basic/" + key))
    val unit = label(if count == 1 then "gradingAttempt" else "gradingAttempts")
    div(cls := "interaction-info__attempts", role := "status",
      title <-- label("gradingAttemptsTooltip").map(_.replace("{count}", count.toString)),
      span(cls := "interaction-info__attempts-count", count.toString),
      span(cls := "interaction-info__attempts-label", child.text <-- unit))
  }

  override def getDomElement(): Element = domElement

}

object InteractionInfoPanel {

  protected val interactionInfoCssString: String = "interaction-element-info"

  /* Tooltip functionality */

  def infoPanelIcon(fullInfo: FullInfo, additionalCssStr: String, svgIcon: ReactiveSvgElement[org.scalajs.dom.SVGSVGElement], tooltipSignal: Signal[String], onMouseClick: Option[MouseEvent => Any]): ReactiveHtmlElement[HTMLDivElement] = {
    div(
      cls := s"info-svg-container ${additionalCssStr} ${if (onMouseClick.nonEmpty) "clickable" else ""}",
      LaminarRenderHelper(fullInfo).createTooltip(tooltipSignal),
      svgIcon,
      onClick --> (mouseEvent => if (onMouseClick.nonEmpty) onMouseClick.get.apply(mouseEvent))
    )
  }


}
