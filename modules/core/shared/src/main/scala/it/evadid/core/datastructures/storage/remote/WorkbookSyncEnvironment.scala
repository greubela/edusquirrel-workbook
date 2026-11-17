package it.evadid.core.datastructures.storage.remote

import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.interaction.sync.{SyncInformationWithContext, UsageContext}

case class WorkbookSyncEnvironment(usageContext: UsageContext, interactions: Set[WorkbookInteractionElement[InteractionFormat]], syncDestinations: Set[SyncInformationWithContext]){

}
