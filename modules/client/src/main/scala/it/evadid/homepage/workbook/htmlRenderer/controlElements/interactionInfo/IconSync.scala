package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.state.StateHelper.RichObservableValue
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionInfoPanelIcons.*
import it.evadid.workbook.abstractions.TypeOfTextDisplay.{PLAINTEXT, PLAINTEXT_UNDERSCORE_REPLACABLE}
import it.evadid.workbook.abstractions.WorkbookInteractionUntyped
import it.evadid.workbook.meta.InteractionVariableSyncReport

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

    InteractionInfoPanel.infoPanelIcon("sync-info", svgSyncIcon, svgSyncTooltip, None).amend(
      role := "img", tabIndex := 0, aria.label <-- svgSyncTooltip,
      span(aria.hidden := true, child.text <-- laminarHelper.plaintextStringSignal("basic/syncInfoLabel")))
  }
}


object IconSync {
  /* Cloud Sync Elements */


}