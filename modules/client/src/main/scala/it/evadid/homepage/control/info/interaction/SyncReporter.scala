package it.evadid.homepage.control.info.interaction

import com.raquo.laminar.api.L.*
import it.evadid.workbook.sync.SyncInformationWithContext
import it.evadid.workbook.reports.InteractionSyncReport

/** A single classification drives both sync icon and tooltip. */
final class SyncReporter(source: Signal[InteractionSyncReport[SyncInformationWithContext]]) {
  val report: Signal[SyncReporter.Report] = source.map(SyncReporter.summarize)
}

object SyncReporter {
  enum Status {
    case Disabled, Unsynced, Partial, Synced
  }
  final case class Report(status: Status, confirmed: Int, total: Int)

  def summarize[A](report: InteractionSyncReport[A]): Report = {
    val total = report.allSyncDestinations.size
    val confirmed = report.destinationsWithSufficientWrites.intersect(report.allSyncDestinations).size
    val status = if total == 0 then Status.Disabled
      else if confirmed == 0 then Status.Unsynced
      else if confirmed == total then Status.Synced
      else Status.Partial
    Report(status, confirmed, total)
  }
}
