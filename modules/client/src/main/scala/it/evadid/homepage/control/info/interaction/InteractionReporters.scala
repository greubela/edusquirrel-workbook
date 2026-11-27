package it.evadid.homepage.control.info.interaction

import it.evadid.homepage.control.change.RemoteInteractionCacheControl
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}

/** Workbook-scoped shared reporters of one interaction. */
final class InteractionReporters[F <: InteractionFormat](val interaction: WorkbookInteractionElement[F], syncControl: RemoteInteractionCacheControl) {
  val grading: GradingReporter = GradingReporter(interaction)
  val sync: SyncReporter = new SyncReporter(syncControl.createReportFor(interaction))
}
