package contentmanagement.model.vm.code.usage

import contentmanagement.model.vm.code.defining.*
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.simulation.{BeExpressionExecutor, BeSimulatorConfig, BeSimulatorState}
import interactionPlugins.blockEnvironment.config.BeTreeDisplayConfig
import interactionPlugins.blockEnvironment.programming.BeExpressionTree
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.data.BeBlockUseValue

case class BeUseValue(value: BeDataValue, contextIfKnown: Option[BeDefineVariable]) extends BeExpression {

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = value match {
    case BeDataValueLiteral(literalStr) if contextIfKnown.nonEmpty =>
      contextIfKnown.get.variableType.formatValueForDisplay(literalStr).getInLanguage(programmingLanguage)
    case BeDataValueLiteral(literalStr) => literalStr
    case reference: BeUseValueReference => reference.variable.name.getInLanguage(humanLanguage)
    case _ => value.displayAsString
  }

  def hasThisExpressionSideEffects: Boolean = false

  def getSyntaxErrorsOfThisStructure: Seq[BeInfo] = List()

  def canEvaluateTo: BeDataType = value.possibleTypes

  override def createBlock(): BeBlock =    BeBlockUseValue(this)

  override def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode]  = List()

  override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = this

}
