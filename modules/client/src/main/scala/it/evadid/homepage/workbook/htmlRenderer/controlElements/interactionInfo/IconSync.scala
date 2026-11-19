package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import com.raquo.laminar.api.L.*
import it.evadid.homepage.control.info.interaction.SyncReporter
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionInfoPanelIcons.*
import it.evadid.workbook.abstractions.TypeOfTextDisplay.*
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}

case class IconSync[F <: InteractionFormat](fullInfo: FullInfo, laminarHelper: LaminarRenderHelper, workbookInteractionElement: WorkbookInteractionElement[F]) {

  lazy val syncReport: Signal[SyncReporter.Report] = fullInfo.signals.reportersFor(workbookInteractionElement).sync.report

  def deriveSyncSymbol(report: SyncReporter.Report): Element = {
    import SyncReporter.Status
    val icon = report.status match {
      case Status.Disabled => createCloudDisabledSvg()
      case Status.Unsynced => createCloudUnsyncedSvg()
      case Status.Partial => createCloudPartiallySyncedSvg()
      case Status.Synced => createCloudFullySyncedSvg()
    }
    val (key, arguments) = report.status match {
      case Status.Disabled => ("basic/syncInfoTooltipDisabled", Seq.empty)
      case Status.Unsynced => ("basic/syncInfoTooltipWarning", Seq(report.total.toString))
      case Status.Partial => ("basic/syncInfoTooltipPartial", Seq(report.confirmed.toString, report.total.toString))
      case Status.Synced => ("basic/syncInfoTooltipFull", Seq(report.total.toString))
    }
    val tooltip = laminarHelper.contentIdStringSignal(key, PLAINTEXT_UNDERSCORE_REPLACABLE, arguments)
    InteractionInfoPanel.infoPanelIcon("sync-info", icon, tooltip, None).amend(
      role := "img", tabIndex := 0, aria.label <-- tooltip,
      span(aria.hidden := true, child.text <-- laminarHelper.plaintextStringSignal("basic/syncInfoLabel")))
  }
}
