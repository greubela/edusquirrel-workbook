package contentmanagement.model.vm.expressions

import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.*
import contentmanagement.model.vm.expressions.BeExpression
import contentmanagement.model.vm.expressions.defining.BeDefineFunction
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeInfo.*
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.parents.BeBlockCallSingleReturnFunction

case class BeFunctionCall(funcDef: BeDefineFunction, withParameter: List[BeUseValue]) extends BeExpression {

  def createBlock(config: BeDisplayConfig, roleInParent: BeChildRole): BeBlock = BeBlockCallSingleReturnFunction(this, roleInParent)

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = ???

  def hasSideEffects: Boolean = funcDef.body.hasSideEffects

  def getSyntaxErrors: Seq[BeInfo] = List()

  def execute(config: BeSimulatorConfig, simulatorState: BeSimulatorState): BeSimulatorState = funcDef.body.execute(config, simulatorState)

  def canEvaluateTo: Set[BeDataType] = funcDef.canEvaluateTo

  
  
  

}
