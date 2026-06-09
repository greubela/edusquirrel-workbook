package todomove.datastructures.core.vm.code.defining

import it.evadid.core.datastructures.language.AppLanguage.{JavaScript, Python}
import BeDefineFunction.*
import todomove.datastructures.core.vm.types.BeChildRole.BodySequence
import todomove.datastructures.core.vm.types.BeScope.InSequenceScope
import it.evadid.core.util.CodeStringBuilder

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.define.BeBlockDefineSingleReturnFunction
import todomove.datastructures.core.vm.code.{BeDefineStructure, BeExpression}
import todomove.datastructures.core.vm.code.controlStructures.BeSequence
import todomove.datastructures.core.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import todomove.datastructures.core.vm.io.BeExpressionIO
import todomove.datastructures.core.vm.static.BeExpressionStaticInformation
import todomove.datastructures.core.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeScope}

case class BeDefineFunction(
                             inputs: List[BeDefineVariable],
                             outputs: Option[BeDefineVariable],
                             body: BeSequence,
                             functionTypeInfo: BeFunctionTypeInfo,
                             indentWidth: Int = 4
                           ) extends BeDefineStructure {


  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {
    /*
        if (!body.canEvaluateTo.exists(curPossibleReturnValue => BeDataType.validForType(body.canEvaluateTo, curPossibleReturnValue))) {
          List(BeInfo(LanguageMap.universalMap("Function Signature Requires [" + canEvaluateTo.mkString(", ") + "] but body returns one of [" + body.canEvaluateTo + "]"), BeInfo.SyntaxError.TypeMismatch))
        } else {
          List()
        }
      }*/

    override def hasSideEffects: Boolean = true
  }


  override def expressionIO: BeExpressionIO = new BeExpressionIO() {
    override def toStringInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, skipUnparsable: Boolean = false): String = {
      def formatTypeHint(variable: BeDefineVariable): Option[String] = {
        variable.variableType match {
          case BeDataType.AnyType => None
          case other => Some(other.formatTypeForDisplay.getInLanguage(programmingLanguage))
        }
      }

      def formatParameter(parameter: BeDefineVariable): String = {
        val base = parameter.name.getInLanguage(humanLanguage)
        programmingLanguage match {
          case Python =>
            formatTypeHint(parameter).map(hint => s"$base: $hint").getOrElse(base)
          case _ => base
        }
      }

      val inputsStr = inputs.map(formatParameter)
      val bodyStr = body.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable)
      val functionName = functionTypeInfo.displayName.getInLanguage(humanLanguage)

      programmingLanguage match {
        case Python =>
          val parameters = inputsStr.mkString("(", ", ", ")")
          val returnAnnotation = outputs.flatMap(output => formatTypeHint(output)).map(hint => s" -> $hint").getOrElse("")
          val indentation = " " * indentWidth
          val bodyLines = if (bodyStr.isEmpty) List(indentation + "pass")
          else {
            bodyStr.split("\n", -1).toList.map { line =>
              if (line.trim.isEmpty) "" else indentation + line
            }
          }
          (s"def $functionName$parameters$returnAnnotation:" :: bodyLines).mkString("\n")
        case JavaScript =>
          val parameters = inputsStr.mkString("(", ", ", ")")
          val builder = CodeStringBuilder()
            .appendNextLine(s"function $functionName$parameters {")
            .changeIntLevel(1)
          if (bodyStr.trim.isEmpty) builder.appendNextLine("// pass")
          else builder.appendAsLines(bodyStr)
          builder
            .changeIntLevel(-1)
            .appendNextLine("}")
            .toString
        case Java | Cpp =>
          val returnType = outputs
            .map(_.variableType.formatTypeForDisplay.getInLanguage(programmingLanguage).trim)
            .filter(_.nonEmpty)
            .getOrElse(if (programmingLanguage == Java) "void" else "auto")
          val parameters = inputs.map { input =>
            val paramType = input.variableType.formatTypeForDisplay.getInLanguage(programmingLanguage).trim
            val renderedType = if (paramType.nonEmpty) paramType else if (programmingLanguage == Java) "Object" else "auto"
            s"$renderedType ${input.name.getInLanguage(humanLanguage)}"
          }.mkString("(", ", ", ")")
          val builder = CodeStringBuilder()
            .appendNextLine(s"$returnType $functionName$parameters {")
            .changeIntLevel(1)
          if (bodyStr.trim.isEmpty) builder.appendNextLine(if (programmingLanguage == Java) "// pass" else "// pass")
          else builder.appendAsLines(bodyStr)
          builder.changeIntLevel(-1).appendNextLine("}").toString
        case Lisp =>
          val parameters = inputs.map(_.name.getInLanguage(humanLanguage).toLowerCase).mkString("(", " ", ")")
          val bodyLines = if (bodyStr.trim.isEmpty) "  nil" else bodyStr.linesIterator.map("  " + _).mkString("\n")
          s"(defun ${functionName.toLowerCase} $parameters\n$bodyLines\n)"
        case _ => ""
      }
    }

    override def toBlock(): BeBlock =
      BeBlockDefineSingleReturnFunction(BeDefineFunction.this)


  }

  /*
  override val toString: String = {

    s"""BeDefineStaticFunction(
       |  ${inputs.map(_.canEvaluateTo.toString).mkString("(", ", ", ")")} => ${outputs.map(_.toString).getOrElse("()")},
       |  $body
       |)""".stripMargin
  }*/

  override def getChildren(withExtensions: Boolean, myScope: BeScope): List[BeExpressionNode] = {
    List(
      BeExpressionReference(BeChildPosition(BeChildRole.BodySequence(0), InSequenceScope(body, myScope)), body),
    )
  }
  
  override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = {
    newChildren.collectFirst {
      case (BodySequence(0), expr) => expr
    }.map(replacement => copy(body = replacement.asInstanceOf[BeSequence])).getOrElse(BeDefineFunction.this)
  }
  
  
}

object BeDefineFunction {

  case class BeFunctionTypeInfo(isMethodInClass: Option[BeDefineClass], isNamed: Option[LanguageMap[HumanLanguage]], funcType: BeFunctionType) {

    def displayName: LanguageMap[HumanLanguage] = isNamed.getOrElse(LanguageMap.universalMap("λ"))

    def displayNamePosition: Int = funcType match {
      case Operator(pos) => pos
      case _ => 0
    }

  }

  sealed trait BeFunctionType

  case class Lambda() extends BeFunctionType

  case class Method() extends BeFunctionType

  case class Function() extends BeFunctionType

  case class Operator(nameBeforeChildNr: Int) extends BeFunctionType

  def methodFunctionInfo(methodInClass: BeDefineClass, name: LanguageMap[HumanLanguage]): BeFunctionTypeInfo = {
    BeFunctionTypeInfo(Some(methodInClass), Some(name), Method())
  }

  def lambdaFunctionInfo(): BeFunctionTypeInfo = {
    BeFunctionTypeInfo(None, None, Lambda())
  }

  def functionInfo(name: LanguageMap[HumanLanguage]): BeFunctionTypeInfo = {
    BeFunctionTypeInfo(None, Some(name), Function())
  }

  def operatorInfo(symbol: String, position: Int): BeFunctionTypeInfo = {
    BeFunctionTypeInfo(None, Some(LanguageMap.universalMap(symbol)), Operator(position))
  }

}

