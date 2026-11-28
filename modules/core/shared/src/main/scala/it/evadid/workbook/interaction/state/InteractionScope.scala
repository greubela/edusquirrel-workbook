package it.evadid.workbook.interaction.state

import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.interaction.sync.{SyncConnection, UsageContext}

/** What a page binds: the sync context, the interactions of the open workbook and the connection of its user. */
final case class InteractionScope(usageContext: UsageContext, interactions: Seq[WorkbookInteractionElement[? <: InteractionFormat]],
    connection: SyncConnection)
