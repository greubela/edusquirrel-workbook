package contentmanagement.model.vm.code.defining

import contentmanagement.model.language.AppLanguage.{Java, JavaScript, Lisp, Python, Rust}
import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.defining.BeDefineFunction.*
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.code.{BeDefineStructure, BeExpression}
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.BodySequence
import interactionPlugins.blockEnvironment.config.BeTreeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.define.BeBlockDefineSingleReturnFunction
case class BeDefineFunction(
    inputs: List[BeDefineVariable],
    outputs: Option[BeDefineVariable],
    body: BeExpression,
    functionTypeInfo: BeFunctionTypeInfo,
    indentWidth: Int = 4
) extends BeDefineStructure {

  override def getSyntaxErrorsOfThisStructure: Seq[BeInfo] = List()
  /*
    if (!body.canEvaluateTo.exists(curPossibleReturnValue => BeDataType.validForType(body.canEvaluateTo, curPossibleReturnValue))) {
      List(BeInfo(LanguageMap.universalMap("Function Signature Requires [" + canEvaluateTo.mkString(", ") + "] but body returns one of [" + body.canEvaluateTo + "]"), BeInfo.SyntaxError.TypeMismatch))
    } else {
      List()
    }
  }*/

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
    def formatTypeHint(dataType: BeDataType): Option[String] = dataType match {
      case BeDataType.AnyType => None
      case other => Some(other.formatTypeForDisplay.getInLanguage(programmingLanguage))
    }

    def formatParameter(parameter: BeDefineVariable): String = {
      val base = parameter.name.getInLanguage(humanLanguage)
      programmingLanguage match {
        case Python => formatTypeHint(parameter.variableType).map(hint => s"$base: $hint").getOrElse(base)
        case _ => base
      }
    }

    val inputsStr = inputs.map(formatParameter)
    val bodyStr = body.getInLanguage(programmingLanguage, humanLanguage)
    val functionName = functionTypeInfo.displayName.getInLanguage(humanLanguage)

    programmingLanguage match {
      case Python =>
        val parameters = inputsStr.mkString("(", ", ", ")")
        val returnAnnotation = outputs.flatMap(output => formatTypeHint(output.variableType)).map(hint => s" -> $hint").getOrElse("")
        val indentation = " " * indentWidth
        val bodyLines = if (bodyStr.isEmpty) List(indentation + "pass")
        else {
          bodyStr.split("\n", -1).toList.map { line =>
            if (line.trim.isEmpty) "" else indentation + line
          }
        }
        (s"def $functionName$parameters$returnAnnotation:" :: bodyLines).mkString("\n")
      case _ => ""
    }
  }

  override def createBlock(): BeBlock =
    BeBlockDefineSingleReturnFunction(this)


  override def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode] = List()

  override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = {
    newChildren.collectFirst {
      case (BodySequence(0), expr) => expr
    }.map(replacement => copy(body = replacement)).getOrElse(this)
  }

  /*
  override val toString: String = {

    s"""BeDefineStaticFunction(
       |  ${inputs.map(_.canEvaluateTo.toString).mkString("(", ", ", ")")} => ${outputs.map(_.toString).getOrElse("()")},
       |  $body
       |)""".stripMargin
  }*/


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

