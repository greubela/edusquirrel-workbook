package contentmanagement.model.vm.code.defining

import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.BeTreeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import interactionPlugins.blockEnvironment.programming.blockdisplay.data.*
import contentmanagement.model.vm.code.*
import contentmanagement.model.vm.code.tree.BeExpressionNode

case class BeDefineVariable(name: LanguageMap[HumanLanguage], val variableType: BeDataType) extends BeDefineStructure {

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = name.getInLanguage(humanLanguage)

  override def hasThisExpressionSideEffects: Boolean = true

  override def getSyntaxErrorsOfThisStructure: Seq[BeInfo] = List()

  override def createBlock(): BeBlock =
    BeBlockDefineVariable(this)

  override def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode] = List()

  override val toString: String = "BeDefineVariable(" + name.toString + ": " + canEvaluateTo + ")"

  override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = this

}


/*
trait BeValueDefinition {

  def currentValue(simulator: BeSimulatorState): Option[String]

  def createBlock(displayConfig: BeDisplayConfig, roleInParent: BeChildRole): BeBlock
}
*/