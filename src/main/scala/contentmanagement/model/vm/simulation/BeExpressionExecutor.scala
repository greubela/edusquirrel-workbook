package contentmanagement.model.vm.simulation

import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.usage.BeUseValue

import scala.collection.mutable


abstract class BeExpressionExecutor(simulatorConfig: BeSimulatorConfig, initSimulatorState: BeSimulatorState, expressionToExecute: BeExpression) {

  def executeRecursivelyInSimulator(): (BeSimulatorState, BeUseValue) = {
    val childrenResults = executeChildrenInSimulatorAndGetValue(initSimulatorState)

    var curSimState = initSimulatorState
    for (curChild <- childrenResults) if (curChild._1 != curSimState) curSimState = curChild._1

    if (expressionToExecute.hasThisExpressionSideEffects)
      curSimState = applySideEffectsOfThisBlock(curSimState, childrenResults)
    
    executeThisBlockInSimulatorAndGetValue(curSimState, childrenResults)
  }

  protected def executeChildrenInSimulatorAndGetValue(simulatorState: BeSimulatorState): List[(BeSimulatorState, BeUseValue)] = {
    val childrenResultBuffer: mutable.ListBuffer[(BeSimulatorState, BeUseValue)] = mutable.ListBuffer()

    for (curChild <- childExpressionsToExecute(simulatorState)) {
      val useSimState = childrenResultBuffer.lastOption.map(_._1).getOrElse(simulatorState)
      val res = curChild.getExecutor( simulatorConfig, useSimState).executeRecursivelyInSimulator()
      childrenResultBuffer += res
    }
    childrenResultBuffer.toList
  }
  
  protected def childExpressionsToExecute(stateBeforeExecution: BeSimulatorState): List[BeExpression]

  protected def applySideEffectsOfThisBlock(stateBeforeExecution: BeSimulatorState, childrenResults: List[(BeSimulatorState, BeUseValue)]): BeSimulatorState

  protected def executeThisBlockInSimulatorAndGetValue(stateBeforeExecution: BeSimulatorState, childrenResults: List[(BeSimulatorState, BeUseValue)]): (BeSimulatorState, BeUseValue)


}

