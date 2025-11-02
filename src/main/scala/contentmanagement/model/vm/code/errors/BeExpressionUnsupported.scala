package contentmanagement.model.vm.code.errors

import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock

case class BeExpressionUnsupported(originalSource: String) extends BeExpression {

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String =
    originalSource

  override def hasThisExpressionSideEffects: Boolean = false

  override def getSyntaxErrorsOfThisStructure: Seq[BeInfo] =
    List(BeInfo(LanguageMap.universalMap(s"Unknown Python structure: $originalSource"), BeInfo.SyntaxError.UnparsableBlock))


  override def canEvaluateTo: BeDataType = BeDataType.Error

  override def createBlock(): BeBlock =
    throw new NotImplementedError("Block rendering is not available for unsupported expressions")

  override def getChildren(withExtensions: Boolean, myScope: BeScope): List[BeExpressionNode] = List()
}
