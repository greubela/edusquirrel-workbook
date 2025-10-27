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

  def createBlock(config: BeDisplayConfig, roleInParent: BeChildRole): BeBlock = BeBlockCallSingleReturnFunction(this, roleInParent)

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = ???

  def hasSideEffects: Boolean = funcDef.body.hasSideEffects

  def getSyntaxErrors: Seq[BeInfo] = List()

  def execute(config: BeSimulatorConfig, simulatorState: BeSimulatorState): BeSimulatorState = funcDef.body.execute(config, simulatorState)

  def canEvaluateTo: Set[BeDataType] = funcDef.canEvaluateTo


  override def getChildren: List[(BeChildRole, BeExpression)] =
    withParameter.zipWithIndex.map((curPar, curIndex) => {
      (FunctionParameter(curIndex), curPar)
    })


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
