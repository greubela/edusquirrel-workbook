package contentmanagement.model.vm.code.defining

import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.{BeDefineStructure, BeExpression}
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock

case class BeManualDefinition(
                               override val definedClasses: List[BeDefineClass],
                               override val definedFunctions: List[BeDefineFunction],
                               override val definedVariables: List[BeDefineVariable]) extends BeDefineStructure {

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = allDefinedStructures.map(_.getInLanguage(programmingLanguage, humanLanguage)).mkString("\n")

  override def getSyntaxErrors: Seq[BeInfo] = allDefinedStructures.flatMap(_.getSyntaxErrors)

  override def createBlock(config: BeDisplayConfig, childPos: BeChildPosition): BeBlock = ???

  override def getChildren: List[(BeChildRole, BeExpression)] = allDefinedStructures.flatMap(_.getChildren)
}
