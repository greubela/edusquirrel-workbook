package contentmanagement.model.vm.code.defining

import contentmanagement.model.language.AppLanguage.{JavaScript, Python}
import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.controlStructures.BeSequence
import contentmanagement.model.vm.code.defining.BeDefineFunction.*
import contentmanagement.model.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import contentmanagement.model.vm.code.{BeDefineStructure, BeExpression}
import contentmanagement.model.vm.io.BeExpressionIO
import contentmanagement.model.vm.static.BeExpressionStaticInformation
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.BodySequence
import contentmanagement.model.vm.types.BeScope.InSequenceScope
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import interactionPlugins.blockEnvironment.programming.blockdisplay.define.BeBlockDefineSingleReturnFunction
import util.CodeStringBuilder

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
    override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
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
      val bodyStr = body.expressionIO.getInLanguage(programmingLanguage, humanLanguage)
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
        case _ => ""
      }
    }

    override def createBlock(): BeBlock =
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

