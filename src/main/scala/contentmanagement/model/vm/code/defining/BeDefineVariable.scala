package contentmanagement.model.vm.code.defining

import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.variable.*
import contentmanagement.model.vm.code.*
import contentmanagement.model.vm.code.usage.{BeUseUnitValue, BeUseValue, BeUseValueLiteral}

case class BeDefineVariable(name: LanguageMap[HumanLanguage], override val canEvaluateTo: Set[BeDataType]) extends BeDefineStructure {


  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = name.getInLanguage(humanLanguage)

  override def hasThisExpressionSideEffects: Boolean = true

  override def getSyntaxErrors: Seq[BeInfo] = List()


  override def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock = BeBlockDefineVariable(this, parentPos)

  def canAcceptValue(value: BeUseValue): Boolean = value.canEvaluateTo.intersect(canEvaluateTo).nonEmpty

  def toUseLiteralWithContext(value: String): BeUseValueLiteral = BeUseValueLiteral(value, Some(this))

  override def getChildren: List[(BeChildRole, BeExpression)] = List()

  override val toString: String = "BeDefineVariable(" + name.toString + ": " + canEvaluateTo.mkString("[", ", ", "]") + ")"

}


/*
trait BeValueDefinition {

  def currentValue(simulator: BeSimulatorState): Option[String]

  def createBlock(displayConfig: BeDisplayConfig, roleInParent: BeChildRole): BeBlock
}
*/