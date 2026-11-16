package it.evadid.core.datastructures.storage.remote

import it.evadid.workbook.abstractions.WorkbookInteractionUntyped
import it.evadid.workbook.interaction.sync.{SyncInformationWithContext, UsageContext}

case class WorkbookSyncEnvironment(usageContext: UsageContext, interactions: Set[WorkbookInteractionUntyped], syncDestinations: Set[SyncInformationWithContext]){

}
