package contentmanagement.model.vm.simulation

import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.vm.types.BeScope
import interactionPlugins.blockEnvironment.programming.*


case class BeSimulatorState(curExecutionPosition: NodeBasedTreePosition, currentScope: BeScope, machineState: BeVirtualMachineState) {
  
}

object BeSimulatorState {
  
  def execute(config: BeSimulatorConfig, program: BeProgram): List[BeSimulatorState] = ???
  
}
