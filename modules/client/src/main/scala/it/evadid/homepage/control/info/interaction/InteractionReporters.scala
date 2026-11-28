package it.evadid.homepage.control.info.interaction

import it.evadid.homepage.control.change.InteractionControl
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}

/** Workbook-scoped shared reporters of one interaction. */
final class InteractionReporters[F <: InteractionFormat](val interaction: WorkbookInteractionElement[F], interactions: InteractionControl) {
  val grading: GradingReporter = GradingReporter(interaction, interactions)
  val sync: SyncReporter = new SyncReporter(interactions.reportSignal(interaction))
}
