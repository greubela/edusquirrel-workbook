package contentmanagement.model.vm.code.usage

import contentmanagement.model.language.AppLanguage.{Java, JavaScript, Lisp, Python, Rust}
import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.*
import contentmanagement.model.vm.code.defining.BeDefineFunction
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.defining.BeDefineFunction.Operator
import contentmanagement.model.vm.code.defining.BeDefineFunction.*
import contentmanagement.model.vm.code.defining.*
import contentmanagement.model.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.FunctionParameter
import contentmanagement.model.vm.types.BeInfo.*
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.using.BeBlockCallSingleReturnFunction

case class BeFunctionCall(funcDef: BeDefineFunction, parameterValueMap: Map[BeDefineVariable, BeExpression]) extends BeExpression {

  private lazy val parameterWithValues: List[(BeDefineVariable, Option[BeExpression])] = funcDef.inputs.map(curInput => (curInput, parameterValueMap.get(curInput)))


  override def createBlock(): BeBlock =
    BeBlockCallSingleReturnFunction(this)

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
    val parameterValuesFormatted: List[String] = parameterWithValues
      .map(
        tup => tup._2.map(curVal => curVal.getInLanguage(programmingLanguage, humanLanguage).replaceAll("\n", "")).getOrElse("")
      )

    val nameStr = funcDef.functionTypeInfo.displayName.getInLanguage(humanLanguage)

    programmingLanguage match {
      case Python | Java | JavaScript | Rust =>
        formatCallForInfix(funcDef.functionTypeInfo.funcType, nameStr, parameterValuesFormatted)
      case _ => ""
    }
  }

  def hasThisExpressionSideEffects: Boolean = false

  def getSyntaxErrorsOfThisStructure: Seq[BeInfo] =
    parameterWithValues.filter(_._2.isEmpty).map(_._1).map(curVal => {
        BeInfo(LanguageMap.universalMap("Missing value for parameter " + curVal.name), BeInfo.SyntaxError.MissingValue)
    })

  def canEvaluateTo: BeDataType = funcDef.outputs.map(_.canEvaluateTo).getOrElse(BeDataType.Unit)

  override def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode] = {
        
    parameterWithValues.zipWithIndex.filter(_._1._2.nonEmpty).map( (tup, index) => (tup._1, tup._2.get, index) ).map( (parVar, parVal, parNr) => {
      BeExpressionReference(BeChildPosition(FunctionParameter(parNr), parentScope), parVal)
    })
  }


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
