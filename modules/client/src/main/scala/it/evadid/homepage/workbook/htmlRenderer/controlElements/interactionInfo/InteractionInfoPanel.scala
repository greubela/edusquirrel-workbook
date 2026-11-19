package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import com.raquo.laminar.api.L.{child, div, *}
import org.scalajs.dom.{HTMLDivElement, MouseEvent}
import com.raquo.laminar.nodes.{ReactiveHtmlElement, ReactiveSvgElement}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionInfoPanel.interactionInfoCssString
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookDisplayElement, WorkbookInteractionElement}
import it.evadid.homepage.control.info.interaction.GradingReporter

case class InteractionInfoPanel[F <: InteractionFormat](
                                 workbookInteractionElement: WorkbookInteractionElement[F],
                                 gradingReporter: GradingReporter)
  extends HtmlAppElement {

  val iconGradingScaffolding = IconGradingAndScaffolding(fullInfo, laminarHelper, workbookInteractionElement, gradingReporter)
  val iconSync = IconSync(fullInfo, laminarHelper, workbookInteractionElement)

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
      )
    )
  }

  override def getDomElement(): Element = domElement

}

object InteractionInfoPanel {

  protected val interactionInfoCssString: String = "interaction-element-info"

  /* Tooltip functionality */

  def infoPanelIcon(additionalCssStr: String, svgIcon: ReactiveSvgElement[org.scalajs.dom.SVGSVGElement], tooltipSignal: Signal[String], onMouseClick: Option[MouseEvent => Any]): ReactiveHtmlElement[HTMLDivElement] = {
    div(
      cls := s"info-svg-container ${additionalCssStr} ${if (onMouseClick.nonEmpty) "clickable" else ""}",
      LaminarRenderHelper.singleton.createTooltip(tooltipSignal),
      svgIcon,
      onClick --> (mouseEvent => if (onMouseClick.nonEmpty) onMouseClick.get.apply(mouseEvent))
    )
  }


}
