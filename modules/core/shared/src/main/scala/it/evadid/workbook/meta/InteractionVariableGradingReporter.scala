package it.evadid.workbook.meta

import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.abstractions.grading.GradedState
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingResult
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory}

case class InteractionVariableGradingReporter[F <: InteractionFormat](
    interaction: WorkbookInteractionElement[F]
) {

  protected def interactionVariable: InteractionVariable[F] = interaction.interactionVariable

  lazy val isScaffoldingSupported: Boolean = interactionVariable.underlyingInteraction.scaffoldingElement.isDefined
  lazy val currentScaffoldingSignal: ObservableValue[Option[ScaffoldingResult[InteractionFormat.State[F], InteractionFormat.Scaffolding[F]]]] = interactionVariable.observableHistoryWithDefault.deriveValue(curHistory => {
    val lastValue: InteractionFormat.State[F] = curHistory.historyStates.lastStateOption.get.value
    interactionVariable.control.getCurrentStateOfScaffolding(lastValue)
  })

  lazy val isGradingSupported: Boolean = interactionVariable.underlyingInteraction.gradingElements.nonEmpty

  lazy val currentHistorySignal: ObservableValue[InteractionVariableHistory[F]] = interactionVariable.observableHistoryWithDefault
  lazy val currentStateSignal: ObservableValue[InteractionFormat.State[F]] = interactionVariable.observableValue
  lazy val currentGradingSignal: ObservableValue[GradedState[InteractionFormat.State[F], InteractionFormat.Grading[F]]] = currentHistorySignal.deriveValue(curHistory => {
    val lastValue: InteractionFormat.State[F] = curHistory.historyStates.lastStateOption.get.value
    interactionVariable.control.getCurrentStateOfGrading(lastValue)
  })

}
