package it.evadid.workbook.vm.simulation

import it.evadid.core.datastructures.tree.nodeImpl.NodeBasedTreePosition
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.workbook.vm.code.BeExpression
import it.evadid.workbook.vm.types.BeScope

case class BeSimulatorState(isMiniStep: Boolean,
                            program: BeExpression,
                            stopped: Boolean,
                            stack: List[BeExpression],
                            scopes: List[BeScope],
                            machineState: BeVirtualMachineState) {

}



object BeSimulatorState {

  def startState(program: BeExpression, curPositionToExecute: NodeBasedTreePosition) = BeSimulatorState(
    true,
    program,
    false,
    List(),
    List(BeScope.GlobalScope()),
    BeVirtualMachineState.emptyMachineState
  )

}
