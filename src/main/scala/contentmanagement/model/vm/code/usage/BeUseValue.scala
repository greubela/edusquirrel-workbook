package contentmanagement.model.vm.code.usage

import contentmanagement.model.vm.code.defining.*
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreeImpl
import contentmanagement.model.language.AppLanguage
import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.simulation.{BeExpressionExecutor, BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.BeExpressionTree
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.variable.BeBlockUseValue

case class BeUseValue(value: BeDataValue, contextIfKnown: Option[BeDefineVariable]) extends BeExpression {
  
  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = value match {
    case BeDataValueLiteral(literalStr) => {
      if(contextIfKnown.nonEmpty) contextIfKnown.get.variableType.formatValueForDisplay(literalStr).getInLanguage(programmingLanguage)
      else literalStr    
    }
    case _ => {
      value.displayAsString
    }
  }

  def hasThisExpressionSideEffects: Boolean = false

  def getSyntaxErrorsOfThisStructure: Seq[BeInfo] = List()

  def canEvaluateTo: BeDataType = value.possibleTypes

  override def createBlock(): BeBlock =    BeBlockUseValue(this)

  override def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode]  = List()

  override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = this

}
