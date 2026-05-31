package todomove.datastructures.core.vm.simulation

import it.evadid.core.datastructures.tree.nodeImpl.NodeBasedTreePosition
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.BeProgram
import todomove.datastructures.core.vm.code.BeExpression
import todomove.datastructures.core.vm.types.BeScope

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
