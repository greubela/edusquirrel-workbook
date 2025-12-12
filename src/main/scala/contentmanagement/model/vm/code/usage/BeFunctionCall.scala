package contentmanagement.model.vm.code.usage

import contentmanagement.model.language.AppLanguage.*
import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.*
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.defining.*
import contentmanagement.model.vm.code.defining.BeDefineFunction.{Operator, *}
import contentmanagement.model.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import contentmanagement.model.vm.io.BeExpressionIO
import contentmanagement.model.vm.static.BeExpressionStaticInformation
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.FunctionParameter
import contentmanagement.model.vm.types.BeInfo.*
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import interactionPlugins.blockEnvironment.programming.blockdisplay.use.BeBlockCallSingleReturnFunction

case class BeFunctionCall(funcDef: BeDefineFunction, parameterValueMap: Map[BeDefineVariable, BeExpression]) extends BeExpression {

  private lazy val parameterWithValues: List[(BeDefineVariable, Option[BeExpression])] = funcDef.inputs.map(curInput => (curInput, parameterValueMap.get(curInput)))

  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {
    override def staticType: BeDataType = funcDef.outputs.map(_.variableType).getOrElse(BeDataType.Unit)

    override def staticValue: Option[BeDataValue] = None

    override def syntaxErrors: Seq[BeInfo] = parameterWithValues.filter(_._2.isEmpty).map(_._1).map(curVal => {
      BeInfo(LanguageMap.universalMap("Missing value for parameter " + curVal.name), BeInfo.SyntaxError.MissingValue)
    })

    override def hasSideEffects: Boolean = false
  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO {
    override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {

      def formatCallForInfix(funcType: BeDefineFunction.BeFunctionType, displayName: String, parameterStrings: List[String]): String = {
        funcType match {
          case Operator(pos) =>
            val left = parameterStrings.slice(0, pos)
            val right = parameterStrings.slice(pos, parameterStrings.length)
            (left :+ displayName :++ right).mkString("(", " ", ")")
          case _ => parameterStrings.mkString(s"$displayName(", ",", ")")
        }
      }

      val parameterValuesFormatted: List[String] = parameterWithValues
        .map(
          tup => tup._2.map(curVal => curVal.expressionIO.getInLanguage(programmingLanguage, humanLanguage).replaceAll("\n", "")).getOrElse("")
        )

      val nameStr = funcDef.functionTypeInfo.displayName.getInLanguage(humanLanguage)

      programmingLanguage match {
        case Python | Java | JavaScript | Rust =>
          formatCallForInfix(funcDef.functionTypeInfo.funcType, nameStr, parameterValuesFormatted)
        case _ => ""
      }
    }


    override def createBlock(): BeBlock = BeBlockCallSingleReturnFunction(BeFunctionCall.this)
  }

  override def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode] = {
    parameterWithValues.zipWithIndex.filter(_._1._2.nonEmpty).map((tup, index) => (tup._1, tup._2.get, index)).map((parVar, parVal, parNr) => {
      BeExpressionReference(BeChildPosition(FunctionParameter(parNr), parentScope), parVal)
    })
  }

  override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = {
    val replacements = newChildren.collect { case (FunctionParameter(nr), expr) => nr -> expr }
    if (replacements.isEmpty) BeFunctionCall.this
    else {
      val updatedMap = replacements.foldLeft(parameterValueMap) { case (acc, (nr, expr)) =>
        funcDef.inputs.lift(nr).map(parameter => acc.updated(parameter, expr)).getOrElse(acc)
      }
      copy(parameterValueMap = updatedMap)
    }
  }

}
