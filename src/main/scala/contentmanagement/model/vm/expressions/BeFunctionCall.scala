package contentmanagement.model.vm.expressions

import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.*
import contentmanagement.model.vm.expressions.defining.BeDefineFunction
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.FunctionParameter
import contentmanagement.model.vm.types.BeInfo.*
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.parents.BeBlockCallSingleReturnFunction
import util.CodeStringBuilder

case class BeFunctionCall(funcDef: BeDefineFunction, withParameter: List[BeUseValue]) extends BeExpression {

  override  def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock = BeBlockCallSingleReturnFunction(this, parentPos)

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = ???

  def hasSideEffects: Boolean = funcDef.body.hasSideEffects

  def getSyntaxErrors: Seq[BeInfo] = List()

  def applySideEffects(config: BeSimulatorConfig, simulatorState: BeSimulatorState): BeSimulatorState = funcDef.body.applySideEffects(config, simulatorState)

  def canEvaluateTo: Set[BeDataType] = funcDef.canEvaluateTo

  override def getChildren: List[(BeChildRole, BeExpression)] =
    withParameter.zipWithIndex.map((curPar, curIndex) => {
      (FunctionParameter(curIndex), curPar)
    })

  override def evaluateBlock(simulatorState: BeSimulatorState): BeUseValue = BeUseUnitValue

  override val toString: String = {

    CodeStringBuilder(s"BeFunctionCall(")
      .changeIntLevel(2)
      .appendNextLine(s"//${funcDef.signature.parameter.map(_.canEvaluateTo.toString).mkString("(", ", ", ")")} <- ${withParameter.mkString("= (", ", ", ")")}")
      .changeIntLevel(-1)
      .appendAsLines(funcDef.toString)
      .changeIntLevel(-1)
      .toString



  }


}
