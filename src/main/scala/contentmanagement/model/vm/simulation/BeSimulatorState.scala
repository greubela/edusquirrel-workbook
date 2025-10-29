package contentmanagement.model.vm.simulation

import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.types.BeScope
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
