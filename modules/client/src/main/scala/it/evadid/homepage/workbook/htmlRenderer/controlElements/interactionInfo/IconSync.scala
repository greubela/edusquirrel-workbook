package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.state.StateHelper.RichObservableValue
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.IconSync.*
import it.evadid.workbook.abstractions.TypeOfTextDisplay.{PLAINTEXT, PLAINTEXT_UNDERSCORE_REPLACABLE}
import it.evadid.workbook.abstractions.WorkbookInteractionUntyped
import it.evadid.workbook.interaction.sync.SyncControl.*
import it.evadid.workbook.meta.InteractionVariableSyncReport
import org.scalajs.dom.{SVGPathElement, SVGSVGElement}

case class IconSync(fullInfo: FullInfo, laminarHelper: LaminarRenderHelper, workbookInteractionElement: WorkbookInteractionUntyped) {

  lazy val syncReport: EventStream[InteractionVariableSyncReport[?, ?, ?]] =
    fullInfo.syncControl.createObservableReport(workbookInteractionElement.interactionVariable).toEventStream()

  def deriveSyncSymbol(report: InteractionVariableSyncReport[?, ?, ?]): Element = {
    val svgSyncIcon: ReactiveSvgElement[org.scalajs.dom.SVGSVGElement] =
      if (report.allSyncLocations.isEmpty) createCloudDisabledSvg()
      else if (report.latestStateIsSyncedTo.isEmpty) createCloudUnsyncedSvg()
      else if (report.latestStateIsSyncedTo.size == report.allSyncLocations.size) createCloudFullySyncedSvg()
      else createCloudPartiallySyncedSvg()

    val svgSyncTooltip: Signal[String] =
      if (report.allSyncLocations.isEmpty) laminarHelper.contentIdStringSignal("basic/syncInfoTooltipDisabled", PLAINTEXT, List())
      else if (report.latestStateIsSyncedTo.isEmpty) laminarHelper.contentIdStringSignal("basic/syncInfoTooltipWarning", PLAINTEXT_UNDERSCORE_REPLACABLE, List[String](report.allSyncLocations.size.toString))
      else if (report.latestStateIsSyncedTo.size == report.allSyncLocations.size) laminarHelper.contentIdStringSignal("basic/syncInfoTooltipFull", PLAINTEXT_UNDERSCORE_REPLACABLE, List[String](report.allSyncLocations.size.toString))
      else laminarHelper.contentIdStringSignal("basic/syncInfoTooltipPartial", PLAINTEXT_UNDERSCORE_REPLACABLE, List[String](report.latestStateIsSyncedTo.size.toString, report.allSyncLocations.size.toString))

    InteractionInfoPanel.infoPanelIcon("sync-info", svgSyncIcon, svgSyncTooltip).amend(
      role := "img", tabIndex := 0, aria.label <-- svgSyncTooltip,
      span(aria.hidden := true, child.text <-- laminarHelper.plaintextStringSignal("basic/syncInfoLabel")))
  }
}


object IconSync {
  /* Cloud Sync Elements */


  private def createCloudSyncSvg(additionalCssStr: String, paths: List[ReactiveSvgElement[SVGPathElement]]): ReactiveSvgElement[SVGSVGElement] = {
    svg.svg(
      svg.cls := s"info-svg sync-info-svg $additionalCssStr",
      aria.hidden := true,
      svg.viewBox := "-0.5 -0.25 16 13"
    ).amend(paths *)
  }

  private def createSvgPath(pathCssString: String, dString: String): ReactiveSvgElement[SVGPathElement] = {
    svg.path(
      svg.cls := s"svg-path ${pathCssString}",
      svg.d := dString
    )
  }


  def createCloudFullySyncedSvg(): ReactiveSvgElement[SVGSVGElement] = {
    createCloudSyncSvg("sync-info-svg-fully", List(
      pathCloudSmall, pathDecoTick
    ))
  }

  def createCloudPartiallySyncedSvg(): ReactiveSvgElement[SVGSVGElement] = {
    createCloudSyncSvg("sync-info-svg-fully", List(
      pathCloudSmall, pathDecoExclamationLine, pathDecoExclamationCircle
    ))
  }

  def createCloudUnsyncedSvg(): ReactiveSvgElement[SVGSVGElement] = {
    createCloudSyncSvg("sync-info-svg-fully", List(
      pathCloudSmall, pathDecoCross
    ))
  }

  def createCloudDisabledSvg(): ReactiveSvgElement[SVGSVGElement] = {
    createCloudSyncSvg("sync-info-svg-fully", List(
      pathCloudSmall, pathDecoMissing
    ))
  }

  def pathCloudSmall: ReactiveSvgElement[SVGPathElement] = svg.path(
    svg.cls := "svg-path path-cloud-small", svg.fill := "none",
    svg.stroke := "currentColor", svg.strokeWidth := "1.05",
    svg.strokeLineCap := "round", svg.strokeLineJoin := "round",
    svg.d := "M3.6 9.8H2.8a2.3 2.3 0 0 1-.4-4.6A4.6 4.6 0 0 1 11.4 4a2.8 2.8 0 0 1 .8 5.5h-1.1")

  def pathDecoMissing: ReactiveSvgElement[SVGPathElement] = createSvgPath("path-deco-missing", "M 7.1249997,7.575 A 2.25,2.25 0 0 0 4.875,9.825 2.25,2.25 0 0 0 7.1249997,12.075 2.25,2.25 0 0 0 9.375,9.825 2.25,2.25 0 0 0 7.1249997,7.575 Z m 0.01692,1.1052627 a 1.1785715,1.1785715 0 0 1 0.287594,0.039471 L 6.0535736,10.090035 A 1.1785715,1.1785715 0 0 1 6.0141014,9.80808 1.1278196,1.1278196 0 0 1 7.1419213,8.6802597 Z M 8.2302663,9.526128 a 1.1785715,1.1785715 0 0 1 0.039471,0.287594 1.1278196,1.1278196 0 0 1 -0.3327065,0.789474 1.1278196,1.1278196 0 0 1 -1.077068,0.281955 z")

  def pathDecoCross: ReactiveSvgElement[SVGPathElement] = createSvgPath("path-deco-cross", "m 8.6221134,7.575 a 0.74855308,0.74855402 0 0 1 0.5314582,0.2214527 0.74855308,0.74855402 0 0 1 0,1.0629173 L 8.1804818,9.825005 9.1535716,10.79064 a 0.74855308,0.74855402 0 0 1 0,1.062962 0.74855308,0.74855402 0 0 1 -1.0629163,0 L 7.1250219,10.880466 6.1593884,11.853602 a 0.74855308,0.74855402 0 0 1 -1.0629599,0 0.74855308,0.74855402 0 0 1 0,-1.062962 L 6.0695621,9.825005 5.0964285,8.85937 A 0.75161586,0.75161681 0 0 1 6.1593884,7.7964527 L 7.1250219,8.769544 8.0906553,7.7964527 A 0.74855308,0.74855402 0 0 1 8.6221134,7.575 Z")

  def pathDecoTick: ReactiveSvgElement[SVGPathElement] = createSvgPath("path-deco-tick", "m 9.3745651,7.575 a 0.74985501,0.74979885 0 0 1 0.5323824,0.2217759 0.74985501,0.74979885 0 0 1 0,1.0572168 L 6.9075573,11.853203 a 0.74985501,0.74979885 0 0 1 -1.0648091,0 l -1.499696,-1.499628 a 0.74985501,0.74979885 0 0 1 0,-1.057216 0.74985501,0.74979885 0 0 1 1.0647649,0 l 0.9673131,0.96724 2.4670534,-2.4668231 A 0.74985501,0.74979885 0 0 1 9.3745651,7.575 Z")

  def pathDecoExclamationLine: ReactiveSvgElement[SVGPathElement] = createSvgPath("path-deco-exclamation-line", "m 7.4942629,6.7936909 a 0.74845709,0.74845709 0 0 0 -0.748472,0.748472 v 2.245372 a 0.74847206,0.74847206 0 0 0 1.496944,0 v -2.245372 a 0.74845709,0.74845709 0 0 0 -0.748472,-0.748472 z")

  def pathDecoExclamationCircle: ReactiveSvgElement[SVGPathElement] = createSvgPath("path-deco-exclamation-circle", "m 7.4929037,11.285837 a 0.74845709,0.74845709 0 0 0 -0.5300304,0.215679 0.78587994,0.78587994 0 0 0 -0.2170824,0.53139 0.74845709,0.74845709 0 0 0 0.2170824,0.53139 0.86072565,0.86072565 0 0 0 0.2469909,0.157176 A 0.74845709,0.74845709 0 0 0 8.1828289,11.748507 0.74845709,0.74845709 0 0 0 8.0256529,11.501516 0.86072565,0.86072565 0 0 0 7.7786616,11.34434 0.74845709,0.74845709 0 0 0 7.4929037,11.28583 Z")

}