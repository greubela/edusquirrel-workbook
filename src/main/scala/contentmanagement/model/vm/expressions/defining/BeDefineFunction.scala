package contentmanagement.model.vm.expressions.defining

import contentmanagement.model.vm.expressions.{BeExpression, BeUseUnitValue, BeUseValue}
import contentmanagement.model.vm.*
import contentmanagement.model.vm.types.BeInfo.*
import interactionPlugins.blockEnvironment.programming.blocks.parents.BeBlockDefineSingleReturnFunction
import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.*
import contentmanagement.model.vm.expressions.BeExpression
import contentmanagement.model.vm.expressions.defining.BeDefineFunction
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.BodySequence
import contentmanagement.model.vm.types.BeInfo.*
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.parents.BeBlockCallSingleReturnFunction

case class BeDefineFunction(signature: BeFunctionSignature, body: BeExpression) extends BeExpression {

  override def getSyntaxErrors: Seq[BeInfo] = List() /*{
    if (!body.canEvaluateTo.exists(curPossibleReturnValue => BeDataType.validForType(body.canEvaluateTo, curPossibleReturnValue))) {
      List(BeInfo(LanguageMap.universalMap("Function Signature Requires [" + canEvaluateTo.mkString(", ") + "] but body returns one of [" + body.canEvaluateTo + "]"), BeInfo.SyntaxError.TypeMismatch))
    } else {
      List()
    }
  }*/

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = ???

  def hasSideEffects: Boolean = true


  def applySideEffects(config: BeSimulatorConfig, simulatorState: BeSimulatorState): BeSimulatorState = simulatorState

  def canEvaluateTo: Set[BeDataType] = signature.returnValue.map(_.canEvaluateTo).getOrElse(Set(BeDataType.Unit))

  def createBlock(config: BeDisplayConfig, roleInParent: BeChildRole): BeBlock = BeBlockDefineSingleReturnFunction(this, roleInParent)

  override def evaluateBlock(simulatorState: BeSimulatorState): BeUseValue = BeUseUnitValue

  override def getChildren: List[(BeChildRole, BeExpression)] = {
    List( (BodySequence(0), body))
  }

  override val toString: String = {

    s"""BeDefineFunction(
       |  ${signature.parameter.map(_.canEvaluateTo.toString).mkString("(", ", ", ")")} => ${signature.returnValue.map(_.toString).getOrElse("()")},
       |  $body
       |)""".stripMargin
  }

}
