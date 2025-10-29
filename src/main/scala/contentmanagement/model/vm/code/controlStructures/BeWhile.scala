package contentmanagement.model.vm.code.controlStructures

import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.usage.BeUseValue
import contentmanagement.model.vm.code.{BeControlStructure, BeExpression}
import contentmanagement.model.vm.simulation.BeSimulatorState
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock

case class BeWhile(
                    conditionSource: BeExpression,
                    body: BeSequence)
  extends BeControlStructure {

  override def allPossibleBodies: List[BeExpression] = List(body)

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = ???


  override def getSyntaxErrors: Seq[BeInfo] = conditionSource.canEvaluateTo.contains(BeDataType.Boolean) match {
    case true => List()
    case false => List(BeInfo(LanguageMap.universalMap("While condition must be able to evaluate to a boolean!"), BeInfo.SyntaxError.TypeMismatch))
  }

  override def createBlock(config: BeDisplayConfig, childPos: BeChildPosition): BeBlock = ???

  override def getChildren: List[(BeChildRole, BeExpression)] = List(
    (BeChildRole.ConditionInControlStructure, conditionSource),
    (BeChildRole.BodySequence(0), body)
  )
}
