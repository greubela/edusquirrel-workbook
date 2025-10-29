package contentmanagement.model.vm.code.defining

import contentmanagement.model.language.AppLanguage.{Java, JavaScript, Lisp, Python, Rust}
import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.defining.BeDefineFunction.*
import contentmanagement.model.vm.code.{BeDefineStructure, BeExpression}
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.BodySequence
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.parents.BeBlockDefineSingleReturnFunction
import util.CodeStringBuilder


case class BeDefineFunction(inputs: List[BeDefineVariable], outputs: Option[BeDefineVariable], body: BeExpression, functionTypeInfo: BeFunctionTypeInfo) extends BeDefineStructure {

  override def getSyntaxErrors: Seq[BeInfo] = List()
  /*
    if (!body.canEvaluateTo.exists(curPossibleReturnValue => BeDataType.validForType(body.canEvaluateTo, curPossibleReturnValue))) {
      List(BeInfo(LanguageMap.universalMap("Function Signature Requires [" + canEvaluateTo.mkString(", ") + "] but body returns one of [" + body.canEvaluateTo + "]"), BeInfo.SyntaxError.TypeMismatch))
    } else {
      List()
    }
  }*/

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
    val inputsStr = inputs.map(_.getInLanguage(programmingLanguage, humanLanguage).replaceAll("\n", ""))
    val bodyStr = body.getInLanguage(programmingLanguage, humanLanguage)
    val functionName = functionTypeInfo.displayName.getInLanguage(humanLanguage)
    val returnType = outputs.map(output => languageSpecificType(programmingLanguage, output.canEvaluateTo)).getOrElse(languageSpecificDefaultReturn(programmingLanguage))
    programmingLanguage match {
      case Python =>
        CodeStringBuilder()
          .appendNextLine(s"def $functionName${inputsStr.mkString("(", ", ", ")")}:")
          .changeIntLevel(1)
          .appendAsLines(bodyStr)
          .toString
      case Java =>
        val parameters = inputs.map { param =>
          val parameterType = languageSpecificType(programmingLanguage, param.canEvaluateTo)
          s"$parameterType ${param.name.getInLanguage(humanLanguage)}"
        }
        val signature = s"${if (returnType.isEmpty) "void" else returnType} $functionName(${parameters.mkString(", ")})"
        CodeStringBuilder()
          .appendNextLine(signature + " {")
          .changeIntLevel(1)
          .appendAsLines(bodyStr)
          .changeIntLevel(-1)
          .appendNextLine("}")
          .toString
      case JavaScript =>
        val parameters = inputs.map(_.name.getInLanguage(humanLanguage)).mkString(", ")
        CodeStringBuilder()
          .appendNextLine(s"function $functionName($parameters) {")
          .changeIntLevel(1)
          .appendAsLines(bodyStr)
          .changeIntLevel(-1)
          .appendNextLine("}")
          .toString
      case Rust =>
        val parameters = inputs.map { param =>
          val paramType = languageSpecificType(programmingLanguage, param.canEvaluateTo)
          s"${param.name.getInLanguage(humanLanguage)}: $paramType"
        }
        val returnClause = if (returnType.isEmpty || returnType == "()") "" else s" -> $returnType"
        CodeStringBuilder()
          .appendNextLine(s"fn ${sanitizeRustName(functionName)}(${parameters.mkString(", ")})$returnClause {")
          .changeIntLevel(1)
          .appendAsLines(bodyStr)
          .changeIntLevel(-1)
          .appendNextLine("}")
          .toString
      case Lisp =>
        val parameters = inputs.map(_.name.getInLanguage(humanLanguage)).mkString(" ")
        CodeStringBuilder(s"(defun ${functionName.toLowerCase} ($parameters)")
          .changeIntLevel(1)
          .appendAsLines(bodyStr)
          .changeIntLevel(-1)
          .appendNextLine(")")
          .toString
      case _ => ""
    }
  }

  private def languageSpecificType(language: ProgrammingLanguage, possibleTypes: Set[BeDataType]): String = {
    val resolvedType = possibleTypes.find(_ != BeDataType.Unit).orElse(possibleTypes.headOption).getOrElse(BeDataType.Unit)
    language match {
      case Java =>
        resolvedType match {
          case BeDataType.Numeric => "double"
          case BeDataType.Boolean => "boolean"
          case BeDataType.String => "String"
          case BeDataType.Date => "java.time.LocalDate"
          case BeDataType.Unit => "void"
          case _ => "Object"
        }
      case Rust =>
        resolvedType match {
          case BeDataType.Numeric => "f64"
          case BeDataType.Boolean => "bool"
          case BeDataType.String => "String"
          case BeDataType.Date => "chrono::NaiveDate"
          case BeDataType.Unit => "()"
          case _ => "()"
        }
      case _ => resolvedType.toString
    }
  }

  private def languageSpecificDefaultReturn(language: ProgrammingLanguage): String = language match {
    case Java => "void"
    case Rust => "()"
    case _ => ""
  }

  private def sanitizeRustName(name: String): String =
    if (name.nonEmpty && name.head.isUpper) name.head.toLower + name.tail else name

  override def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock = BeBlockDefineSingleReturnFunction(this, parentPos)

  override protected def changedScopeForChildren(parentScope: BeScope): BeScope = BeScope.InFunctionScope(this, parentScope)

  override def getChildren: List[(BeChildRole, BeExpression)] = {
    List((BodySequence(0), body))
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

