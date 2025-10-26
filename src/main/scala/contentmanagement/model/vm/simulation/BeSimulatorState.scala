package contentmanagement.model.vm.simulation

import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import interactionPlugins.blockEnvironment.programming.*


case class BeSimulatorState(curExecutionPosition: NodeBasedTreePosition, machineState: BeVirtualMachineState) {
  
}

object BeSimulatorState {
  
  def execute(config: BeSimulatorConfig, program: BeProgram): List[BeSimulatorState] = ???
  
}
