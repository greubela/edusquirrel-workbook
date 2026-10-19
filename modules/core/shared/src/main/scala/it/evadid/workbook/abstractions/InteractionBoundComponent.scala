package it.evadid.workbook.abstractions

import it.evadid.core.datastructures.state.State
import it.evadid.workbook.interaction.variable.InteractionVariable

trait InteractionBoundComponent[T] {
  private val binding = State(Option.empty[WorkbookInteractionElementFull[T, ?, ?]])

  final def interaction: WorkbookInteractionElementFull[T, ?, ?] =
    binding.now().getOrElse(throw IllegalStateException("This component is not bound to an interaction."))

  final def interactionVariable: InteractionVariable[T, ?, ?] = interaction.interactionVariable

  private def validateBinding(owner: WorkbookInteractionElementFull[T, ?, ?]): Unit =
    require(binding.now().forall(_ eq owner), "This component already belongs to another interaction.")

  private def bind(owner: WorkbookInteractionElementFull[T, ?, ?]): Unit =
    binding.update {
      case None => Some(owner)
      case existing => existing
    }
}

object InteractionBoundComponent {
  private[workbook] def bindAll[T](owner: WorkbookInteractionElementFull[T, ?, ?],
      components: List[InteractionBoundComponent[T]]): Unit =
    if components.nonEmpty then synchronized {
      components.foreach(_.validateBinding(owner))
      components.foreach(_.bind(owner))
    }
}
