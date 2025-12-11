package contentmanagement.model.vm.code

import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.BeDataType

trait BeControlStructure extends BeExpression {

  def allPossibleBodies: List[BeExpression]

}
