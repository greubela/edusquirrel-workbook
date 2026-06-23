package it.evadid.workbook.vm.code.defining

import it.evadid.core.datastructures.language.AppLanguage.{JavaScript, Python}
import BeDefineFunction.*
import it.evadid.workbook.vm.types.BeChildRole.BodySequence
import it.evadid.workbook.vm.types.BeScope.InSequenceScope
import it.evadid.core.util.CodeStringBuilder

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.workbook.vm.code.{BeDefineStructure, BeExpression}
import it.evadid.workbook.vm.code.controlStructures.BeSequence
import it.evadid.workbook.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.workbook.vm.io.BeExpressionIO
import it.evadid.workbook.vm.naming.{BeEntityName, CodeRepresentationConfig}
import it.evadid.workbook.vm.static.BeExpressionStaticInformation
import it.evadid.workbook.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeScope}

case class BeDefineFunction(
                             inputs: List[BeDefineVariable],
                             outputs: Option[BeDefineVariable],
                             body: BeSequence,
                             functionTypeInfo: BeFunctionTypeInfo,
                             indentWidth: Int = 4
                           ) extends BeDefineStructure {

  /*
  toSnapPattern
   */

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
    override def toStringWithConfig(config: CodeRepresentationConfig): String = {
      import config.{programmingLanguage, humanLanguage, skipUnparsable}
      def formatTypeHint(variable: BeDefineVariable): Option[String] = {
        variable.variableType match {
          case BeDataType.AnyType => None
          case other => Some(other.formatTypeForDisplay.getInLanguage(programmingLanguage))
        }
      }

      def formatParameter(parameter: BeDefineVariable): String = {
        val base = parameter.name.getNameIn(humanLanguage, config.namingStyle)
        programmingLanguage match {
          case Python =>
            formatTypeHint(parameter).map(hint => s"$base: $hint").getOrElse(base)
          case _ => base
        }
      }

      val inputsStr = inputs.map(formatParameter)
      val bodyStr = body.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable)
      val functionName = functionTypeInfo.displayName.getNameIn(humanLanguage, config.namingStyle)

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
            s"$renderedType ${input.name.getNameIn(humanLanguage, config.namingStyle)}"
          }.mkString("(", ", ", ")")
          val builder = CodeStringBuilder()
            .appendNextLine(s"$returnType $functionName$parameters {")
            .changeIntLevel(1)
          if (bodyStr.trim.isEmpty) builder.appendNextLine(if (programmingLanguage == Java) "// pass" else "// pass")
          else builder.appendAsLines(bodyStr)
          builder.changeIntLevel(-1).appendNextLine("}").toString
        case Lisp =>
          val parameters = inputs.map(_.name.getNameIn(humanLanguage, config.namingStyle).toLowerCase).mkString("(", " ", ")")
          val bodyLines = if (bodyStr.trim.isEmpty) "  nil" else bodyStr.linesIterator.map("  " + _).mkString("\n")
          s"(defun ${functionName.toLowerCase} $parameters\n$bodyLines\n)"
        case _ => ""
      }
    }



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

  case class BeFunctionTypeInfo(isMethodInClass: Option[BeDefineClass], isNamed: Option[BeEntityName], funcType: BeFunctionType) {

    def displayName: BeEntityName = isNamed.getOrElse(BeEntityName.fromUniversalNameInParts("λ"))

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

  def methodFunctionInfo(methodInClass: BeDefineClass, name: BeEntityName): BeFunctionTypeInfo = {
    BeFunctionTypeInfo(Some(methodInClass), Some(name), Method())
  }

  def lambdaFunctionInfo(): BeFunctionTypeInfo = {
    BeFunctionTypeInfo(None, None, Lambda())
  }

  def functionInfo(name: BeEntityName): BeFunctionTypeInfo = {
    BeFunctionTypeInfo(None, Some(name), Function())
  }

  def operatorInfo(symbol: String, position: Int): BeFunctionTypeInfo = {
    BeFunctionTypeInfo(None, Some(BeEntityName.fromUniversalNameInParts(symbol)), Operator(position))
  }

}

