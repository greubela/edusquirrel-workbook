package it.evadid.workbook.meta

import it.evadid.core.datastructures.storage.remote.RemoteHistoryCache
import it.evadid.core.datastructures.storage.remote.RemoteHistoryReaderWriter
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.interaction.sync.{EventSyncStrategy, SyncContext, SyncInformationWithContext}
import it.evadid.workbook.interaction.variable.{InteractionVariableHistory, InteractionVariableState}
import upickle.default.*

import java.time.LocalDateTime

/** Report for an interaction variable showing sync status across all destinations.
  * Uses the event-based cache model for tracking local and remote histories. */
case class InteractionVariableSyncReport[K, T, GA, SA, RW <: RemoteHistoryReaderWriter[K, T, GA, SA]]
(
  curLocalHistory: InteractionVariableHistory[T, GA, SA],
  cacheReport: RemoteHistoryCache.CacheReportForKey[K, T, GA, SA, RW],
  allSyncDestinations: Set[RW]
) {

  def destinationsWithSufficientWrites(): Set[RW] = {
    allSyncDestinations.filter { destination =>
      val required = curLocalHistory.cleanWithStrategy(syncStrategyForDestination(destination))
      val remote = cacheReport.lastKnownValues.getOrElse(destination, RemoteHistoryCache.RemoteHistoryValue.empty).history
      remote.mergeWith(required) == remote
    }
  }

  def syncStrategyForDestination(destination: RW): EventSyncStrategy = {
    // Use a default strategy; in a real implementation, this would be stored per-destination
    EventSyncStrategy.KEEP_MAJOR
  }

  lazy val allInformationEverywhere: InteractionVariableHistory[T, GA, SA] = {
    val allHistories = cacheReport.lastKnownValues.values.map(_.history).toList
    allHistories.fold(curLocalHistory)(_.mergeWith(_))
  }

}
