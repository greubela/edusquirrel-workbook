package contentmanagement.model.vm.code.usage

import contentmanagement.model.language.AppLanguage.Python
import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.*
import contentmanagement.model.vm.code.defining.BeDefineFunction
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.defining.BeDefineFunction.Operator
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.FunctionParameter
import contentmanagement.model.vm.types.BeInfo.*
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.parents.BeBlockCallSingleReturnFunction
import util.CodeStringBuilder

case class BeFunctionCall(funcDef: BeDefineFunction, withParameterValues: List[BeUseValue]) extends BeExpression {

  override def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock = BeBlockCallSingleReturnFunction(this, parentPos)

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
    val withParameterValuesStr = withParameterValues.map(_.getInLanguage(programmingLanguage, humanLanguage).replaceAll("\n", ""))
    val nameStr = funcDef.functionTypeInfo.displayName.getInLanguage(humanLanguage)

    programmingLanguage match {
      case Python => {
        funcDef.functionTypeInfo.funcType match {
          case Operator(pos) => "(" + withParameterValues.slice(0, pos).mkString(", ") + nameStr + withParameterValues.slice(pos, withParameterValues.length).mkString(", ") + ")"
          case _ => withParameterValuesStr.mkString(s"$nameStr(", ",", ")")
        }
      }
      case _ => ""
    }
  }

  def hasThisExpressionSideEffects: Boolean = false

  def getSyntaxErrors: Seq[BeInfo] = List()

  def canEvaluateTo: Set[BeDataType] = funcDef.outputs.map(_.canEvaluateTo).getOrElse(Set(BeDataType.Unit))

  override def getChildren: List[(BeChildRole, BeExpression)] =   withParameterValues.zipWithIndex.map((curPar, curIndex) => {
    (FunctionParameter(curIndex), curPar)
  })


}
