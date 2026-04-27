package datastructures.core.vm.simulation

import datastructures.core.vm.code.BeExpression
import datastructures.core.vm.types.BeScope
import interactionPlugins.blockEnvironment.programming.*
import it.evadid.core.datastructures.tree.nodeImpl.NodeBasedTreePosition

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*

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
