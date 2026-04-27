package datastructures.core.vm.code.usage

import datastructures.core.vm.code.defining.BeDefineFunction.{Operator, *}
import datastructures.core.vm.types.BeChildRole.FunctionParameter
import datastructures.core.vm.types.BeInfo.*
import datastructures.core.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import interactionPlugins.blockEnvironment.programming.blockdisplay.use.BeBlockCallSingleReturnFunction
import datastructures.core.language.*
import datastructures.core.language.AppLanguage.*
import datastructures.core.vm.code.BeExpression
import datastructures.core.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import datastructures.core.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import datastructures.core.vm.io.BeExpressionIO
import datastructures.core.vm.static.BeExpressionStaticInformation
import datastructures.core.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeDataValue, BeInfo, BeScope}

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

      val operatorPrecedence: Map[String, Int] = Map(
        "or" -> 1,
        "and" -> 2,
        "not" -> 3,
        "==" -> 4,
        "!=" -> 4,
        "<" -> 4,
        "<=" -> 4,
        ">" -> 4,
        ">=" -> 4,
        "is" -> 4,
        "is not" -> 4,
        "|" -> 5,
        "^" -> 6,
        "&" -> 7,
        "<<" -> 8,
        ">>" -> 8,
        "+" -> 9,
        "-" -> 9,
        "*" -> 10,
        "/" -> 10,
        "//" -> 10,
        "%" -> 10,
        "**" -> 12
      )

      val operatorTokens: List[String] = operatorPrecedence.keys.toList.sortBy(-_.length)

      def wrapOperand(operand: String, parentOperator: String, isLeft: Boolean): String = {
        val parentPrecedence = operatorPrecedence(parentOperator)
        val childOperators = collectTopLevelOperators(operand)
        val needsParentheses = childOperators.exists { op =>
          val childPrecedence = operatorPrecedence.getOrElse(op, Int.MaxValue)
          childPrecedence < parentPrecedence ||
          (childPrecedence == parentPrecedence && requiresGroupingForEqualPrecedence(parentOperator, isLeft))
        }
        if (needsParentheses) s"($operand)" else operand
      }

      def collectTopLevelOperators(expression: String): List[String] = {
        val operators = scala.collection.mutable.ListBuffer.empty[String]
        var index = 0
        var depth = 0
        while (index < expression.length) {
          expression.charAt(index) match {
            case '(' => depth += 1; index += 1
            case ')' => depth = math.max(0, depth - 1); index += 1
            case _ if depth == 0 =>
              val maybeOperator = operatorTokens.find(token => expression.startsWith(token, index) && isOperatorBoundary(expression, index, token.length))
              maybeOperator match {
                case Some(token) =>
                  operators += token
                  index += token.length
                case None => index += 1
              }
            case _ => index += 1
          }
        }
        operators.toList
      }

      def isOperatorBoundary(expression: String, start: Int, length: Int): Boolean = {
        def isIdent(ch: Char): Boolean = ch.isLetterOrDigit || ch == '_'
        val before = if (start > 0) expression.charAt(start - 1) else ' '
        val after = if (start + length < expression.length) expression.charAt(start + length) else ' '
        val requiresWordBoundary = (0 until length).exists(i => expression.charAt(start + i).isLetter)
        !requiresWordBoundary || (!isIdent(before) && !isIdent(after))
      }

      def requiresGroupingForEqualPrecedence(operator: String, isLeftOperand: Boolean): Boolean = {
        val rightSensitive = Set("-", "/", "//", "%", "**")
        rightSensitive.contains(operator) && !(!isLeftOperand && operator == "**")
      }

      def formatCallForInfix(funcType: BeDefineFunction.BeFunctionType, displayName: String, parameterStrings: List[String]): String = {
        funcType match {
          case Operator(pos) =>
            val left = parameterStrings.slice(0, pos)
            val right = parameterStrings.slice(pos, parameterStrings.length)
            val isUnary = parameterStrings.length == 1
            if (isUnary) {
              val operand = parameterStrings.headOption.getOrElse("")
              val needsSpace = displayName.exists(_.isLetterOrDigit) || displayName.contains(" ")
              if (needsSpace) s"$displayName $operand" else s"$displayName$operand"
            } else {
              val wrappedLeft = left.map(part => wrapOperand(part, displayName, isLeft = true))
              val wrappedRight = right.map(part => wrapOperand(part, displayName, isLeft = false))
              (wrappedLeft ++ List(displayName) ++ wrappedRight).mkString(" ")
            }
          case _ => parameterStrings.mkString(s"$displayName(", ", ", ")")
        }
      }

      val parameterValuesFormatted: List[String] = parameterWithValues
        .map(
          tup => tup._2.map(curVal => curVal.expressionIO.getInLanguage(programmingLanguage, humanLanguage).replaceAll("\n", "")).getOrElse("")
        )

      val nameStr = funcDef.functionTypeInfo.displayName.getInLanguage(humanLanguage)

      programmingLanguage match {
        case Python | Java | JavaScript | Rust | Cpp =>
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
