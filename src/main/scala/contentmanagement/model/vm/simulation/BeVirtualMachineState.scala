package contentmanagement.model.vm.simulation

import contentmanagement.model.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.usage.BeUseValue
import contentmanagement.model.vm.types.*

case class BeVirtualMachineState(
                                  knownFunctions: List[BeDefineFunction],
                                  variableValues: Map[BeDefineVariable, BeUseValue],
                                  isMicroStep: Boolean) {
  
  
}
