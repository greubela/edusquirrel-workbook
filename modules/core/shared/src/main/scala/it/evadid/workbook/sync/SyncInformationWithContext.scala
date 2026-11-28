package it.evadid.workbook.sync

import it.evadid.workbook.sync.destination.{SyncDestinationDefinition, SyncDestinationHistoryHandler}

/** Resolves a destination once for a workbook/user/credential environment. */
case class SyncInformationWithContext(syncInformation: SyncInformation, usageContext: UsageContext,
    resolveDefinition: SyncDestinationDefinition => SyncDestinationHistoryHandler) {
  val handler: SyncDestinationHistoryHandler = resolveDefinition(syncInformation.syncDestination)
}
