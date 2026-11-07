package it.evadid.workbook.meta

import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.workbook.abstractions.grading.GradedState
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingResult
import it.evadid.workbook.interaction.variable.InteractionVariable

case class InteractionVariableGradingReporter[T, GA, SA]
(
  interactionVariable: InteractionVariable[T, GA, SA],
) {

  lazy val isScaffoldingSupported: Boolean = interactionVariable.underlyingInteraction.scaffoldingElement.isDefined
  lazy val currentScaffoldingSignal: ObservableValue[Option[ScaffoldingResult[T, SA]]] = interactionVariable.observableHistoryWithDefault.deriveValue(curHistory => {
    val lastValue: T = curHistory.historyStates.lastStateOption.get.value
    interactionVariable.control.getCurrentStateOfScaffolding(lastValue)
  })

  lazy val isGradingSupported: Boolean = interactionVariable.underlyingInteraction.gradingElements.nonEmpty

  lazy val currentStateSignal: ObservableValue[T] = interactionVariable.observableValue
  lazy val currentGradingSignal: ObservableValue[GradedState[T, GA]] = interactionVariable.observableHistoryWithDefault.deriveValue(curHistory => {
    val lastValue: T = curHistory.historyStates.lastStateOption.get.value
    interactionVariable.control.getCurrentStateOfGrading(lastValue)
  })

}
