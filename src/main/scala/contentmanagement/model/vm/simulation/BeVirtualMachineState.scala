package contentmanagement.model.vm.simulation

import contentmanagement.model.vm.expressions.defining.{BeDefineVariable, BeDefineFunction}
import contentmanagement.model.vm.expressions.{BeExpression, BeUseValue}
import contentmanagement.model.vm.types.*

case class BeVirtualMachineState(
                                  knownFunctions: List[BeDefineFunction],
                                  variableValues: Map[BeDefineVariable, BeUseValue],
                                  isMicroStep: Boolean) {
  
  
}
