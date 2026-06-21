package it.evadid.workbook.vm.simulation

import it.evadid.workbook.vm.code.BeExpression
import it.evadid.workbook.vm.types.BeDataValue

import scala.collection.mutable


abstract class BeExpressionExecutor(simulatorConfig: BeSimulatorConfig, initSimulatorState: BeSimulatorState, expressionToExecute: BeExpression) {

  def executeRecursivelyInSimulator(): (BeSimulatorState, BeDataValue) = {
    val childrenResults = executeChildrenInSimulatorAndGetValue(initSimulatorState)

    var curSimState = initSimulatorState
    for (curChild <- childrenResults) if (curChild._1 != curSimState) curSimState = curChild._1

    if (expressionToExecute.staticInformationExpression.hasSideEffects)
      curSimState = applySideEffectsOfThisBlock(curSimState, childrenResults)
    
    executeThisBlockInSimulatorAndGetValue(curSimState, childrenResults)
  }

  protected def executeChildrenInSimulatorAndGetValue(simulatorState: BeSimulatorState): List[(BeSimulatorState, BeDataValue)] = {
    val childrenResultBuffer: mutable.ListBuffer[(BeSimulatorState, BeDataValue)] = mutable.ListBuffer()

    for (curChild <- childExpressionsToExecute(simulatorState)) {
      val useSimState = childrenResultBuffer.lastOption.map(_._1).getOrElse(simulatorState)
      val res = curChild.expressionExecutor( simulatorConfig, useSimState).executeRecursivelyInSimulator()
      childrenResultBuffer += res
    }
    childrenResultBuffer.toList
  }
  
  protected def childExpressionsToExecute(stateBeforeExecution: BeSimulatorState): List[BeExpression] 

  protected def applySideEffectsOfThisBlock(stateBeforeExecution: BeSimulatorState, childrenResults: List[(BeSimulatorState, BeDataValue)]): BeSimulatorState

  protected def executeThisBlockInSimulatorAndGetValue(stateBeforeExecution: BeSimulatorState, childrenResults: List[(BeSimulatorState, BeDataValue)]): (BeSimulatorState, BeDataValue)


}

