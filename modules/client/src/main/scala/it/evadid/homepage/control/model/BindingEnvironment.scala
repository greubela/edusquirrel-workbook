package it.evadid.homepage.control.model

import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.elements.structureElements.Workbook
import it.evadid.workbook.interaction.binding.SyncConnection
import it.evadid.workbook.interaction.sync.UsageContext

/** What the open page binds: the sync context, the loaded workbook instance and the connection of the user. */
final case class BindingEnvironment(usageContext: UsageContext, workbook: Option[Workbook], connection: SyncConnection) {
  def interactions: Seq[WorkbookInteractionElement[? <: InteractionFormat]] =
    workbook.toSeq.flatMap(_.allContainedInteractionsById.values)
}
