package contentmanagement.model.vm.code.errors

import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.ConditionInControlStructure
import contentmanagement.model.vm.types.BeScope.InSequenceScope
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock

case class BeExpressionUnparsable(originalSource: String, message: String) extends BeExpression {

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String =
    originalSource

  override def hasThisExpressionSideEffects: Boolean = false

  override def getSyntaxErrorsOfThisStructure: Seq[BeInfo] =
    List(BeInfo(LanguageMap.universalMap(message), BeInfo.SyntaxError.UnparsableBlock))


  override def canEvaluateTo: BeDataType = BeDataType.Error

  override def createBlock(): BeBlock =
    throw new NotImplementedError("Block rendering is not available for unparsable expressions")

  override def getChildren(withExtensions: Boolean, myScope: BeScope): List[BeExpressionNode] = List()

  override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = this


}
