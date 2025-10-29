package contentmanagement.model.vm.code.errors

import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.usage.{BeUseUnitValue, BeUseValue}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock

case class BeExpressionUnsupported(originalSource: String) extends BeExpression {

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String =
    originalSource

  override def hasThisExpressionSideEffects: Boolean = false

  override def getSyntaxErrors: Seq[BeInfo] =
    List(BeInfo(LanguageMap.universalMap(s"Unknown Python structure: $originalSource"), BeInfo.SyntaxError.UnparsableBlock))

  override def applySideEffects(config: BeSimulatorConfig, simulatorState: BeSimulatorState): BeSimulatorState = simulatorState
  override def evaluateBlock(simulatorState: BeSimulatorState): BeUseValue = BeUseUnitValue

  override def canEvaluateTo: Set[BeDataType] = Set(BeDataType.Error)

  override def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock = ???

  override def getChildren: List[(BeChildRole, BeExpression)] = List()
}
