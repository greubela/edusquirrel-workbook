package it.evadid.workbook.abstractions

import it.evadid.core.datastructures.state.State
import it.evadid.workbook.interaction.variable.InteractionVariable

trait InteractionBoundComponent[T] {
  private val binding = State(Option.empty[InteractionVariable[T, ?, ?]])

  final def interactionVariable: InteractionVariable[T, ?, ?] =
    binding.now().getOrElse(throw IllegalStateException("This component is not bound to an interaction variable."))

  final def interaction: WorkbookInteractionElementFull[T, ?, ?] = interactionVariable.underlyingInteraction

  private def validateBinding(variable: InteractionVariable[T, ?, ?]): Unit =
    require(binding.now().forall(_ eq variable), "This component already belongs to another interaction variable.")

  private def bind(variable: InteractionVariable[T, ?, ?]): Unit =
    binding.update {
      case None => Some(variable)
      case existing => existing
    }
}

object InteractionBoundComponent {
  private[workbook] def bindAll[T](variable: InteractionVariable[T, ?, ?],
      components: List[InteractionBoundComponent[T]]): Unit =
    if components.nonEmpty then synchronized {
      components.foreach(_.validateBinding(variable))
      components.foreach(_.bind(variable))
    }
}
