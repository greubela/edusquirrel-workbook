package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import com.raquo.laminar.api.L.{child, *}
import com.raquo.laminar.nodes.{ReactiveHtmlElement, ReactiveSvgElement}
import it.evadid.core.datastructures.state.StateHelper.RichObservableValue
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionInfoPanel.interactionInfoCssString
import it.evadid.workbook.abstractions.WorkbookInteractionUntyped
import org.scalajs.dom.HTMLDivElement

case class InteractionInfoPanel(workbookInteractionElement: WorkbookInteractionUntyped) extends HtmlAppElement {

  val iconGrading = IconGrading(fullInfo, laminarHelper, workbookInteractionElement)
  val iconScaffolding = IconScaffolding(fullInfo, laminarHelper, workbookInteractionElement)
  val iconSync = IconSync(fullInfo, laminarHelper, workbookInteractionElement)


  lazy val domElement: Element = {
    div(
      cls := interactionInfoCssString,
      div(cls := "interaction-info__icons",
        div(cls := "grading-report-icon", child <-- iconGrading.gradingReporter.currentGradingSignal.toEventStream().map(iconGrading.deriveGradingSymbol)),
        div(cls := "sync-report-icon", child <-- iconSync.syncReport.map(iconSync.deriveSyncSymbol)),
        Option.when(workbookInteractionElement.scaffoldingElement.nonEmpty)(iconScaffolding.hintButton)),
      child.maybe <-- iconScaffolding.expanded.signal.map(open => Option.when(open)(iconScaffolding.hintBody()))
    )
  }

  override def getDomElement(): Element = domElement

}

object InteractionInfoPanel {

  def withEditor(interaction: WorkbookInteractionUntyped, editor: HtmlAppElement): HtmlAppElement =
    if interaction.scaffoldingElement.isEmpty && interaction.gradingElements.isEmpty then editor
    else new HtmlAppElement {
      override def dismissOnOutsideClick: Boolean = editor.dismissOnOutsideClick

      override def getDomElement(): Element = div(cls := "interaction-editor",
        InteractionInfoPanel(interaction).getDomElement(),
        div(cls := "interaction-editor__content", editor.getDomElement()))
    }

  /* CSS info */

  protected val interactionInfoCssString: String = "interaction-element-info"

  /* Tooltip functionality */

  def infoPanelIcon(additionalCssStr: String, svgIcon: ReactiveSvgElement[org.scalajs.dom.SVGSVGElement], tooltipSignal: Signal[String]): ReactiveHtmlElement[HTMLDivElement] = div(
    cls := s"info-svg-container ${additionalCssStr}",
    LaminarRenderHelper.singleton.createTooltip(tooltipSignal),
    svgIcon,
  )


}
