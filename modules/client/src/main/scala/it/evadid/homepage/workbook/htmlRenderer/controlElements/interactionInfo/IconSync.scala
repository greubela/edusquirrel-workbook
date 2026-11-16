package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.state.StateHelper.RichObservableValue
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionInfoPanelIcons.*
import it.evadid.workbook.abstractions.TypeOfTextDisplay.{PLAINTEXT, PLAINTEXT_UNDERSCORE_REPLACABLE}
import it.evadid.workbook.abstractions.{WorkbookInteractionElementFull, WorkbookInteractionUntyped}
import it.evadid.workbook.meta.InteractionVariableSyncReport

case class IconSync[T, GA, SA](fullInfo: FullInfo, laminarHelper: LaminarRenderHelper, workbookInteractionElement: WorkbookInteractionElementFull[T, GA, SA]) {

  lazy val syncReport: Signal[Option[InteractionVariableSyncReport[?, ?, ?]]] =
    fullInfo.syncControl.createReportFor(workbookInteractionElement)

  def deriveSyncSymbol(reportOp: Option[InteractionVariableSyncReport[?, ?, ?]]): Element = {
    if (reportOp.isEmpty) createCloudDisabledSvg() else {
      val report = reportOp.get
      val svgSyncIcon: ReactiveSvgElement[org.scalajs.dom.SVGSVGElement] =
        if (report.allSyncLocations.isEmpty) createCloudDisabledSvg()
        else if (report.destinationsWithSufficientWrites().isEmpty) createCloudUnsyncedSvg()
        else if (report.destinationsWithSufficientWrites().size == report.allSyncLocations.size) createCloudFullySyncedSvg()
        else createCloudPartiallySyncedSvg()

      val svgSyncTooltip: Signal[String] =
        if (report.allSyncLocations.isEmpty) laminarHelper.contentIdStringSignal("basic/syncInfoTooltipDisabled", PLAINTEXT, List())
        else if (report.destinationsWithSufficientWrites().isEmpty) laminarHelper.contentIdStringSignal("basic/syncInfoTooltipWarning", PLAINTEXT_UNDERSCORE_REPLACABLE, List[String](report.allSyncLocations.size.toString))
        else if (report.destinationsWithSufficientWrites().size == report.allSyncLocations.size) laminarHelper.contentIdStringSignal("basic/syncInfoTooltipFull", PLAINTEXT_UNDERSCORE_REPLACABLE, List[String](report.allSyncLocations.size.toString))
        else laminarHelper.contentIdStringSignal("basic/syncInfoTooltipPartial", PLAINTEXT_UNDERSCORE_REPLACABLE, List[String](report.destinationsWithSufficientWrites().size.toString, report.allSyncLocations.size.toString))

      InteractionInfoPanel.infoPanelIcon("sync-info", svgSyncIcon, svgSyncTooltip, None).amend(
        role := "img", tabIndex := 0, aria.label <-- svgSyncTooltip,
        span(aria.hidden := true, child.text <-- laminarHelper.plaintextStringSignal("basic/syncInfoLabel")))
    }
  }
}


object IconSync {
  /* Cloud Sync Elements */


}