package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.state.StateHelper.RichObservableValue
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionInfoPanelIcons.*
import it.evadid.workbook.abstractions.TypeOfTextDisplay.*
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.interaction.sync.SyncContext
import it.evadid.workbook.meta.InteractionVariableSyncReport
import it.evadid.workbook.interaction.sync.SyncInformationWithContext

case class IconSync[F <: InteractionFormat](fullInfo: FullInfo, laminarHelper: LaminarRenderHelper, workbookInteractionElement: WorkbookInteractionElement[F]) {

  lazy val syncReport: Signal[Option[InteractionVariableSyncReport[SyncInformationWithContext]]] =
    fullInfo.syncControl.createReportFor(workbookInteractionElement)

  def deriveSyncSymbol(reportOp: Option[InteractionVariableSyncReport[SyncInformationWithContext]]): Element = {
    if (reportOp.isEmpty) createCloudDisabledSvg() else {
      val report = reportOp.get
      val svgSyncIcon: ReactiveSvgElement[org.scalajs.dom.SVGSVGElement] =
        if (report.allSyncDestinations.isEmpty) createCloudDisabledSvg()
        else if (report.destinationsWithSufficientWrites.isEmpty) createCloudUnsyncedSvg()
        else if (report.destinationsWithSufficientWrites.size == report.allSyncDestinations.size) createCloudFullySyncedSvg()
        else createCloudPartiallySyncedSvg()

      val svgSyncTooltip: Signal[String] =
        if (report.allSyncDestinations.isEmpty) laminarHelper.contentIdStringSignal("basic/syncInfoTooltipDisabled", PLAINTEXT, List())
        else if (report.destinationsWithSufficientWrites.isEmpty) laminarHelper.contentIdStringSignal("basic/syncInfoTooltipWarning", PLAINTEXT_UNDERSCORE_REPLACABLE, Seq[String](report.allSyncDestinations.size.toString))
        else if (report.destinationsWithSufficientWrites.size == report.allSyncDestinations.size) laminarHelper.contentIdStringSignal("basic/syncInfoTooltipFull", PLAINTEXT_UNDERSCORE_REPLACABLE, Seq[String](report.allSyncDestinations.size.toString))
        else laminarHelper.contentIdStringSignal("basic/syncInfoTooltipPartial", PLAINTEXT_UNDERSCORE_REPLACABLE, Seq[String](report.destinationsWithSufficientWrites.size.toString, report.allSyncDestinations.size.toString))

      InteractionInfoPanel.infoPanelIcon("sync-info", svgSyncIcon, svgSyncTooltip, None).amend(
        role := "img", tabIndex := 0, aria.label <-- svgSyncTooltip,
        span(aria.hidden := true, child.text <-- laminarHelper.plaintextStringSignal("basic/syncInfoLabel")))
    }
  }


}

object IconSync {
  /* Cloud Sync Elements */


}
