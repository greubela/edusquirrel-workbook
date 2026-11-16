package it.evadid.workbook.meta

import it.evadid.core.datastructures.storage.remote.RemoteHistoryCache
import it.evadid.workbook.interaction.sync.EventSyncStrategy
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import upickle.default.*

import java.time.LocalDateTime

/** Report for an interaction variable showing sync status across all destinations.
  * Uses the event-based cache model for tracking local and remote histories. */
case class InteractionVariableSyncReport[K, T, GA, SA, RW](
  curLocalHistory: InteractionVariableHistory[T, GA, SA],
  cacheReport: RemoteHistoryCache.CacheReportForKey[K, T, GA, SA, RW],
  allSyncDestinations: Set[RW]
) {

  def destinationsWithSufficientWrites(): Set[RW] = {
    allSyncDestinations.filter { destination =>
      val required = curLocalHistory.cleanWithStrategy(EventSyncStrategy.KEEP_MAJOR)
      val remote = cacheReport.lastKnownValues.getOrElse(destination, RemoteHistoryCache.RemoteHistoryValue.empty).history
      remote.mergeWith(required) == remote
    }
  }

  lazy val allInformationEverywhere: InteractionVariableHistory[T, GA, SA] = {
    val allHistories = cacheReport.lastKnownValues.values.map(_.history).toList
    allHistories.fold(curLocalHistory)(_.mergeWith(_))
  }

}
