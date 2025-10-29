package contentmanagement.model.vm.code

import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.BeDataType

trait BeControlStructure extends BeExpression {

  override def hasThisExpressionSideEffects: Boolean = false

  override def canEvaluateTo: Set[BeDataType] = allPossibleBodies.flatMap(_.canEvaluateTo).toSet

  def allPossibleBodies: List[BeExpression]

}
