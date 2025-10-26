package contentmanagement.model.vm.expressions.defining

import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.expressions.{BeExpression, BeUseValue}
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.{BeChildRole, BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.variable.*

import contentmanagement.model.vm.expressions.*
import contentmanagement.model.vm.expressions.BeUseValue

case class BeDefineVariable(name: LanguageMap[HumanLanguage], override val canEvaluateTo: Set[BeDataType]) extends BeExpression {

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = name.getInLanguage(humanLanguage)

  override def hasSideEffects: Boolean = true

  override def getSyntaxErrors: Seq[BeInfo] = List()

  override def execute(config: BeSimulatorConfig, simulatorState: BeSimulatorState): BeSimulatorState = ???

  override def createBlock(config: BeDisplayConfig, roleInParent: BeChildRole): BeBlock = BeBlockDefineVariable(this, roleInParent)

  def canAcceptValue(value: BeUseValue): Boolean = value.canEvaluateTo.intersect(canEvaluateTo).nonEmpty

  def toUseLiteralBlock(value: String): BeBlock = BeBlockUseLiteralForVariable(BeUseValueLiteral(value), BeChildRole.ValueForVariable(this))

}


/*
trait BeValueDefinition {

  def currentValue(simulator: BeSimulatorState): Option[String]

  def createBlock(displayConfig: BeDisplayConfig, roleInParent: BeChildRole): BeBlock
}
*/