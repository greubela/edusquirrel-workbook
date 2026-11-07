package it.evadid.workbook.meta

import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory}

case class InteractionVariableGradingReport[T, GA, SA]
(
  interactionVariable: InteractionVariable[T, GA, SA],
  curLocalHistory: InteractionVariableHistory[T, GA, SA],
) {

  def isScaffoldingSupported: Boolean = interactionVariable.underlyingInteraction.scaffoldingElement.isDefined


  def isGradingSupported: Boolean = interactionVariable.underlyingInteraction.gradingElements.nonEmpty

  /*def allGradingResults: List[GradingResult[T, GA]] = {

    interactionVariable.

    val lastState = curLocalHistory.historyStates.lastStateOption

    if(lastState.isEmpty) List() else {
      curLocalHistory.historyGrading
    }
  }*/

}
