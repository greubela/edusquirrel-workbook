package contentmanagement.model.vm.expressions.controlStructures

import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.expressions.{BeExpression, BeSequence, BeUseUnitValue, BeUseValue}
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.{BeChildRole, BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock

import scala.collection.mutable.ListBuffer

case class BeExpressionIfElse(
                               conditionSource: BeExpression,
                               ifBody: BeSequence,
                               elseBody: BeSequence
                             ) extends BeExpression {

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = ???

  override def hasSideEffects: Boolean = false

  override def getSyntaxErrors: Seq[BeInfo] =
    if (!conditionSource.canEvaluateTo.contains(BeDataType.Boolean)) List(BeInfo(LanguageMap.universalMap("if/else condition must evaluate to a boolean!"), BeInfo.SyntaxError.TypeMismatch))
    else List()

  override def applySideEffects(config: BeSimulatorConfig, simulatorState: BeSimulatorState): BeSimulatorState = simulatorState

  override def canEvaluateTo: Set[BeDataType] = Set(BeDataType.Unit)

  override def createBlock(config: BeDisplayConfig, roleInParent: BeChildRole): BeBlock = ???


  override def evaluateBlock(simulatorState: BeSimulatorState): BeUseValue = BeUseUnitValue
  
  override def getChildren: List[(BeChildRole, BeExpression)] = {
    List((BeChildRole.ConditionInControlStructure, conditionSource),
      (BeChildRole.BodySequence(0), ifBody),
      (BeChildRole.BodySequence(1), elseBody)
    )      

  }

  private def renderBody(
                          expressions: List[BeExpression],
                          programmingLanguage: ProgrammingLanguage,
                          humanLanguage: HumanLanguage
                        ): List[String] = {
    if (expressions.isEmpty) List()
    else {
      expressions.flatMap { expr =>
        val rendered = expr.getInLanguage(programmingLanguage, humanLanguage)
        if (rendered.isEmpty) List("    ")
        else rendered.linesIterator.map(line => s"    ${line}").toList
      }
    }
  }
}
