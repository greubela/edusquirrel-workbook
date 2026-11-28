package it.evadid.homepage.control.info.interaction

import com.raquo.laminar.api.L.*
import it.evadid.homepage.control.change.InteractionControl
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.session.AllWorkbookInfo

/** One set of shared reporters per interaction of the loaded workbook; only replacing that workbook instance
  * replaces them. Icons, sidebars, inline panels and editors read the same reporter instances.
  */
final class InteractionReporterRegistry(workbook: Signal[Option[AllWorkbookInfo]], interactions: InteractionControl, owner: Owner) {

  val all: StrictSignal[Map[WorkbookInteractionElement[? <: InteractionFormat], InteractionReporters[? <: InteractionFormat]]] =
    workbook.map(_.map(_.loadedWorkbook))
      .distinctByFn((previous, next) => previous.zip(next).exists((a, b) => a eq b) || (previous.isEmpty && next.isEmpty))
      .map(_.toSeq.flatMap(_.allContainedInteractionsById.values).map { interaction =>
        interaction -> new InteractionReporters(interaction, interactions)
      }.toMap).observe(using owner)

  def reportersFor(interaction: WorkbookInteractionElement[? <: InteractionFormat]): InteractionReporters[? <: InteractionFormat] =
    all.now().get(interaction).filter(entry => entry.interaction eq interaction).getOrElse(
      throw new IllegalArgumentException(s"Interaction ${interaction.elementId} does not belong to the loaded workbook"))
}
