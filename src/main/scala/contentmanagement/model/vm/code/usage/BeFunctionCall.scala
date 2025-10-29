package contentmanagement.model.vm.code.usage

import contentmanagement.model.language.AppLanguage.{Java, JavaScript, Lisp, Python, Rust}
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

case class BeFunctionCall(funcDef: BeDefineFunction, withParameterValues: List[BeUseValue]) extends BeExpression {

  override def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock = BeBlockCallSingleReturnFunction(this, parentPos)

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
    val withParameterValuesStr = withParameterValues.map(_.getInLanguage(programmingLanguage, humanLanguage).replaceAll("\n", ""))
    val nameStr = funcDef.functionTypeInfo.displayName.getInLanguage(humanLanguage)

    programmingLanguage match {
      case Python =>
        formatCallForInfix(funcDef.functionTypeInfo.funcType, nameStr, withParameterValuesStr)
      case Java | JavaScript | Rust =>
        formatCallForInfix(funcDef.functionTypeInfo.funcType, nameStr, withParameterValuesStr)
      case Lisp =>
        s"(${nameStr.toLowerCase}${if (withParameterValuesStr.isEmpty) "" else " " + withParameterValuesStr.mkString(" ")})"
      case _ => ""
    }
  }

  def hasThisExpressionSideEffects: Boolean = false

  def getSyntaxErrors: Seq[BeInfo] = List()

  def canEvaluateTo: Set[BeDataType] = funcDef.outputs.map(_.canEvaluateTo).getOrElse(Set(BeDataType.Unit))

  override def getChildren: List[(BeChildRole, BeExpression)] =   withParameterValues.zipWithIndex.map((curPar, curIndex) => {
    (FunctionParameter(curIndex), curPar)
  })


  private def formatCallForInfix(funcType: BeDefineFunction.BeFunctionType, displayName: String, parameterStrings: List[String]): String = {
    funcType match {
      case Operator(pos) =>
        val left = parameterStrings.slice(0, pos)
        val right = parameterStrings.slice(pos, parameterStrings.length)
        (left :+ displayName :++ right).mkString("(", " ", ")")
      case _ => parameterStrings.mkString(s"$displayName(", ",", ")")
    }
  }

}
