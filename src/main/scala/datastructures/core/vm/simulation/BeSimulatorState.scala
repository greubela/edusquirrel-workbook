package datastructures.core.vm.simulation

import datastructures.core.tree.nodeImpl.NodeBasedTreePosition
import datastructures.core.vm.code.BeExpression
import datastructures.core.vm.types.BeScope
import interactionPlugins.blockEnvironment.programming.*


case class BeSimulatorState(isMiniStep: Boolean,
                            program: BeProgram,
                            stopped: Boolean,
                            stack: List[BeExpression],
                            scopes: List[BeScope],
                            machineState: BeVirtualMachineState) {

}



object BeSimulatorState {

  def startState(program: BeProgram, curPositionToExecute: NodeBasedTreePosition) = BeSimulatorState(
    true,
    program,
    false,
    List(),
    List(BeScope.GlobalScope()),
    BeVirtualMachineState.emptyMachineState
  )

}
