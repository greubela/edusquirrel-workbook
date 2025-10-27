package contentmanagement.model.vm.expressions

import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.{BeChildRole, BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock

case class BeExpressionUnkown(originalSource: String) extends BeExpression {

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String =
    originalSource

  override def hasSideEffects: Boolean = false

  override def getSyntaxErrors: Seq[BeInfo] =
    List(BeInfo(LanguageMap.universalMap(s"Unknown Python structure: $originalSource"), BeInfo.SyntaxError.UnparsableBlock))

  override def applySideEffects(config: BeSimulatorConfig, simulatorState: BeSimulatorState): BeSimulatorState = simulatorState
  override def evaluateBlock(simulatorState: BeSimulatorState): BeUseValue = BeUseUnitValue

  override def canEvaluateTo: Set[BeDataType] = Set(BeDataType.Error)

  override def createBlock(config: BeDisplayConfig, roleInParent: BeChildRole): BeBlock = ???

  override def getChildren: List[(BeChildRole, BeExpression)] = List()
}
