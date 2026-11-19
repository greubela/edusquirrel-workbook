package it.evadid.homepage.control.info.interaction

import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}

/** Workbook-scoped shared reporters. Sync stays lazy to avoid a registry/controller initialization cycle. */
final class InteractionReporters[F <: InteractionFormat](val interaction: WorkbookInteractionElement[F], fullInfo: FullInfo) {
  val grading: GradingReporter = GradingReporter(interaction)
  lazy val sync: SyncReporter = new SyncReporter(fullInfo.syncControl.createReportFor(interaction))
}
