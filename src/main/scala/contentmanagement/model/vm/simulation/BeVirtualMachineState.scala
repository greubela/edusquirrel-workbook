package contentmanagement.model.vm.simulation

import contentmanagement.model.vm.code.BeDefineStructure
import contentmanagement.model.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import contentmanagement.model.vm.code.usage.BeUseValue
import contentmanagement.model.vm.types.*


case class BeVirtualMachineState(
                                  knownClasses: Map[BeDefineClass, List[BeScope]],
                                  knownFunctions: Map[BeDefineFunction, List[BeScope]],
                                  knownVariables: Map[BeDefineVariable, List[BeScope]],
                                  variableValues: Map[BeDefineVariable, BeUseValue]
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
