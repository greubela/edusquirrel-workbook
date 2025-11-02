package contentmanagement.model.vm.code

import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.BeDataType

trait BeControlStructure extends BeExpression {

  override def hasThisExpressionSideEffects: Boolean = false

  override def canEvaluateTo: BeDataType = BeDataType.Unit

  def allPossibleBodies: List[BeExpression]
    
  
}
