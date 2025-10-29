package contentmanagement.model.vm.code

import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import contentmanagement.model.vm.code.usage.{BeUseUnitValue, BeUseValue}
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock

trait BeDefineStructure extends BeExpression{

  def definedClasses: List[BeDefineClass] = List()

  def definedFunctions: List[BeDefineFunction] = List()

  def definedVariables: List[BeDefineVariable] = List()
  
  override def hasThisExpressionSideEffects: Boolean = true

  override def applySideEffects(config: BeSimulatorConfig, simulatorState: BeSimulatorState): BeSimulatorState = ???

  override def evaluateBlock(simulatorState: BeSimulatorState): BeUseValue = BeUseUnitValue

  override def canEvaluateTo: Set[BeDataType] = Set(BeDataType.Unit)

  

}
