package it.evadid.homepage.control.info.interaction

import com.raquo.laminar.api.L.*
import it.evadid.workbook.interaction.sync.SyncInformationWithContext
import it.evadid.workbook.meta.InteractionVariableSyncReport

/** A single classification drives both sync icon and tooltip. */
final class SyncReporter(source: Signal[Option[InteractionVariableSyncReport[SyncInformationWithContext]]]) {
  /** Distinct: every poll re-reports each interaction, and views rebuild their icon for each new report. */
  val report: Signal[SyncReporter.Report] = source.map(SyncReporter.summarize).distinct
}

object SyncReporter {
  enum Status {
    case Disabled, Unsynced, Partial, Synced
  }
  final case class Report(status: Status, confirmed: Int, total: Int)

  def summarize[A](report: Option[InteractionVariableSyncReport[A]]): Report = {
    val total = report.fold(0)(_.allSyncDestinations.size)
    val confirmed = report.fold(0)(r => r.destinationsWithSufficientWrites.intersect(r.allSyncDestinations).size)
    val status = if total == 0 then Status.Disabled
      else if confirmed == 0 then Status.Unsynced
      else if confirmed == total then Status.Synced
      else Status.Partial
    Report(status, confirmed, total)
  }
}
