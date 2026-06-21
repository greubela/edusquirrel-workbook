package it.evadid.workbook.vm.simulation

import it.evadid.workbook.vm.code.BeDefineStructure
import it.evadid.workbook.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import it.evadid.workbook.vm.types.{BeDataValue, BeScope}


case class BeVirtualMachineState(
                                  knownClasses: Map[BeDefineClass, List[BeScope]],
                                  knownFunctions: Map[BeDefineFunction, List[BeScope]],
                                  knownVariables: Map[BeDefineVariable, List[BeScope]],
                                  variableValues: Map[BeDefineVariable, BeDataValue]
                                ) {


  def isDefined(objects: BeDefineStructure): Boolean = {
    objects.definedClasses.forall(knownClasses.contains) &&
      objects.definedFunctions.forall(knownFunctions.contains) &&
      objects.definedVariables.forall(knownVariables.contains)

  }

  def isAvailableInScope(objects: BeDefineStructure, scope: BeScope): Boolean = {

    objects.definedClasses.forall(curClass => knownClasses.contains(curClass) && knownClasses(curClass).contains(scope)) &&
      objects.definedFunctions.forall(curFunc => knownFunctions.contains(curFunc) && knownFunctions(curFunc).contains(scope)) &&
      objects.definedVariables.forall(curVar => knownVariables.contains(curVar) && knownVariables(curVar).contains(scope))
  }

}

object BeVirtualMachineState {

  val emptyMachineState = BeVirtualMachineState(Map(), Map(), Map(), Map())

}
