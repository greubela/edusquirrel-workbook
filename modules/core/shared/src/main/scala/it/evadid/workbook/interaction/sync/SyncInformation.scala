package it.evadid.workbook.interaction.sync

import it.evadid.workbook.interaction.sync.destination.{SyncDestinationDefinition, SyncDestinationHistoryHandler}
import upickle.default.*

case class SyncInformation(
                            syncDestination: SyncDestinationDefinition,
                            syncStrategy: EventSyncStrategy
                          ) derives ReadWriter {

  def withoutCredentials: SyncInformation = copy(syncDestination = syncDestination.withoutCredentials)

  def forContext(context: UsageContext, resolver: SyncDestinationDefinition => SyncDestinationHistoryHandler): SyncInformationWithContext = SyncInformationWithContext(this, context, resolver)

}



