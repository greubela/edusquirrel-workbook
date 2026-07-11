package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L.{svg as svgtag, *}
import com.raquo.laminar.nodes.{ReactiveHtmlElement, ReactiveSvgElement}
import it.evadid.core.datastructures.state.StateHelper.RichObservableValue
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.controlElements.InteractionInfoPanel.*
import it.evadid.workbook.abstractions.TypeOfTextDisplay.{PLAINTEXT, PLAINTEXT_UNDERSCORE_REPLACABLE}
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.interaction.sync.SyncControl.InteractionVariableSyncReport
import org.scalajs.dom.{SVGSVGElement, html}

case class InteractionInfoPanel(workbookInteractionElement: WorkbookInteractionElement[Any]) extends HtmlAppElement {

  lazy val report: EventStream[InteractionVariableSyncReport[Any]] = fullInfo.syncControl.createObservableReport[Any](workbookInteractionElement.interactionVariable).toEventStream()

  lazy val domElement: Element = div(
    cls := interactionInfoCssString,
    children <-- report.map(deriveChildren)
  )

  override def getDomElement(): Element = domElement


  def deriveChildren(report: InteractionVariableSyncReport[?]): List[Element] = {

    val (svgSyncIcon, svgSyncTooltip): (ReactiveSvgElement[org.scalajs.dom.SVGSVGElement], Signal[String]) = {
      if (report.allSyncLocations.isEmpty) (
        createCloudDisabledSvg(),
        laminarHelper.contentIdStringSignal("basic/syncInfoTooltipDisabled", PLAINTEXT, List())
      )
      else if (report.latestStateIsSyncedTo.isEmpty) (
        createCloudUnsyncedSvg(),
        laminarHelper.contentIdStringSignal("basic/syncInfoTooltipWarning", PLAINTEXT_UNDERSCORE_REPLACABLE, List[String](report.allSyncLocations.size.toString))
      )
      else if (report.latestStateIsSyncedTo.size == report.allSyncLocations.size) (
        createCloudFullySyncedSvg(),
        laminarHelper.contentIdStringSignal("basic/syncInfoTooltipFull", PLAINTEXT_UNDERSCORE_REPLACABLE, List[String](report.allSyncLocations.size.toString))
      )
      else (
        createCloudPartiallySyncedSvg(),
        laminarHelper.contentIdStringSignal("basic/syncInfoTooltipPartial", PLAINTEXT_UNDERSCORE_REPLACABLE, List[String](report.latestStateIsSyncedTo.size.toString, report.allSyncLocations.size.toString))
      )
    }

    val syncInfo = div(
      cls := "sync-info",
      createTooltip(svgSyncTooltip),
      svgSyncIcon,
    )

    List(syncInfo)

  }
}

object InteractionInfoPanel {

  def createTooltip(text: String): Seq[Modifier[ReactiveHtmlElement[html.Element]]] = {
    Seq(
      cls := "custom-tooltip-target",
      dataAttr("tooltip") := text
    )
  }

  def createTooltip(text: Signal[String]): Seq[Modifier[ReactiveHtmlElement[html.Element]]] = {
    Seq(
      cls := "custom-tooltip-target",
      dataAttr("tooltip") <-- text
    )
  }

  protected val interactionInfoCssString: String = "interaction-element-info"

  def createCloudFullySyncedSvg(): ReactiveSvgElement[SVGSVGElement] = svgtag.svg(
    // replace
    svgtag.cls := "info-status-svg",
    svgtag.viewBox("0 0 24 24"),
    svgtag.xmlns("http://www.w3.org/2000/svg"),
    svgtag.path(
      svgtag.cls := "svg-error-path",
      svgtag.d("M 11.97 12.54 A 3.99 3.99 0 0 0 7.98 16.53 A 3.99 3.99 0 0 0 11.97 20.52 A 3.99 3.99 0 0 0 15.96 16.53 A 3.99 3.99 0 0 0 11.97 12.54 z M 12 14.50002 A 2.09 2.09 0 0 1 12.51 14.56998 L 10.06998 16.99998 A 2.09 2.09 0 0 1 10.00002 16.5 A 2 2 0 0 1 12 14.50002 z M 13.93002 16.00002 A 2.09 2.09 0 0 1 13.99998 16.51002 A 2 2 0 0 1 13.41 17.91 A 2 2 0 0 1 11.50002 18.40998 L 13.93002 16.00002 z ")
    ),
    svgtag.path(
      svgtag.cls := "svg-cloud-path",
      svgtag.d("M 18.42,7.72 A 7,7 0 0 0 5.06,9.61 4,4 0 0 0 4.68,17.27 1.13,1.13 0 0 0 5,17.32 1.0127191,1.0127191 0 0 0 5.32,15.32 2,2 0 0 1 4,13.5 a 2,2 0 0 1 2,-2 1,1 0 0 0 1,-1 5,5 0 0 1 9.73,-1.61 1,1 0 0 0 0.78,0.67 3,3 0 0 1 1,5.53 1.0034441,1.0034441 0 1 0 1,1.74 A 5,5 0 0 0 22,12.5 5,5 0 0 0 18.42,7.72 Z")
    )
  )

  def createCloudPartiallySyncedSvg(): ReactiveSvgElement[SVGSVGElement] = svgtag.svg(
    // replace
    svgtag.cls := "info-status-svg",
    svgtag.viewBox("0 0 24 24"),
    svgtag.xmlns("http://www.w3.org/2000/svg"),
    svgtag.path(
      svgtag.cls := "svg-error-path",
      svgtag.d("M 11.97 12.54 A 3.99 3.99 0 0 0 7.98 16.53 A 3.99 3.99 0 0 0 11.97 20.52 A 3.99 3.99 0 0 0 15.96 16.53 A 3.99 3.99 0 0 0 11.97 12.54 z M 12 14.50002 A 2.09 2.09 0 0 1 12.51 14.56998 L 10.06998 16.99998 A 2.09 2.09 0 0 1 10.00002 16.5 A 2 2 0 0 1 12 14.50002 z M 13.93002 16.00002 A 2.09 2.09 0 0 1 13.99998 16.51002 A 2 2 0 0 1 13.41 17.91 A 2 2 0 0 1 11.50002 18.40998 L 13.93002 16.00002 z ")
    ),
    svgtag.path(
      svgtag.cls := "svg-cloud-path",
      svgtag.d("M 18.42,7.72 A 7,7 0 0 0 5.06,9.61 4,4 0 0 0 4.68,17.27 1.13,1.13 0 0 0 5,17.32 1.0127191,1.0127191 0 0 0 5.32,15.32 2,2 0 0 1 4,13.5 a 2,2 0 0 1 2,-2 1,1 0 0 0 1,-1 5,5 0 0 1 9.73,-1.61 1,1 0 0 0 0.78,0.67 3,3 0 0 1 1,5.53 1.0034441,1.0034441 0 1 0 1,1.74 A 5,5 0 0 0 22,12.5 5,5 0 0 0 18.42,7.72 Z")
    )
  )

  def createCloudUnsyncedSvg(): ReactiveSvgElement[SVGSVGElement] = svgtag.svg(
    // replace
    svgtag.cls := "info-status-svg",
    svgtag.viewBox("0 0 24 24"),
    svgtag.xmlns("http://www.w3.org/2000/svg"),
    svgtag.path(
      svgtag.cls := "svg-error-path",
      svgtag.d("M 11.97 12.54 A 3.99 3.99 0 0 0 7.98 16.53 A 3.99 3.99 0 0 0 11.97 20.52 A 3.99 3.99 0 0 0 15.96 16.53 A 3.99 3.99 0 0 0 11.97 12.54 z M 12 14.50002 A 2.09 2.09 0 0 1 12.51 14.56998 L 10.06998 16.99998 A 2.09 2.09 0 0 1 10.00002 16.5 A 2 2 0 0 1 12 14.50002 z M 13.93002 16.00002 A 2.09 2.09 0 0 1 13.99998 16.51002 A 2 2 0 0 1 13.41 17.91 A 2 2 0 0 1 11.50002 18.40998 L 13.93002 16.00002 z ")
    ),
    svgtag.path(
      svgtag.cls := "svg-cloud-path",
      svgtag.d("M 18.42,7.72 A 7,7 0 0 0 5.06,9.61 4,4 0 0 0 4.68,17.27 1.13,1.13 0 0 0 5,17.32 1.0127191,1.0127191 0 0 0 5.32,15.32 2,2 0 0 1 4,13.5 a 2,2 0 0 1 2,-2 1,1 0 0 0 1,-1 5,5 0 0 1 9.73,-1.61 1,1 0 0 0 0.78,0.67 3,3 0 0 1 1,5.53 1.0034441,1.0034441 0 1 0 1,1.74 A 5,5 0 0 0 22,12.5 5,5 0 0 0 18.42,7.72 Z")
    )
  )

  def createCloudDisabledSvg(): ReactiveSvgElement[SVGSVGElement] = svgtag.svg(
    svgtag.cls := "info-status-svg",
    svgtag.viewBox("0 0 24 24"),
    svgtag.xmlns("http://www.w3.org/2000/svg"),
    svgtag.path(
      svgtag.cls := "svg-error-path",
      svgtag.d("M 11.97 12.54 A 3.99 3.99 0 0 0 7.98 16.53 A 3.99 3.99 0 0 0 11.97 20.52 A 3.99 3.99 0 0 0 15.96 16.53 A 3.99 3.99 0 0 0 11.97 12.54 z M 12 14.50002 A 2.09 2.09 0 0 1 12.51 14.56998 L 10.06998 16.99998 A 2.09 2.09 0 0 1 10.00002 16.5 A 2 2 0 0 1 12 14.50002 z M 13.93002 16.00002 A 2.09 2.09 0 0 1 13.99998 16.51002 A 2 2 0 0 1 13.41 17.91 A 2 2 0 0 1 11.50002 18.40998 L 13.93002 16.00002 z ")
    ),
    svgtag.path(
      svgtag.cls := "svg-cloud-path",
      svgtag.d("M 18.42,7.72 A 7,7 0 0 0 5.06,9.61 4,4 0 0 0 4.68,17.27 1.13,1.13 0 0 0 5,17.32 1.0127191,1.0127191 0 0 0 5.32,15.32 2,2 0 0 1 4,13.5 a 2,2 0 0 1 2,-2 1,1 0 0 0 1,-1 5,5 0 0 1 9.73,-1.61 1,1 0 0 0 0.78,0.67 3,3 0 0 1 1,5.53 1.0034441,1.0034441 0 1 0 1,1.74 A 5,5 0 0 0 22,12.5 5,5 0 0 0 18.42,7.72 Z")
    )
  )


}
