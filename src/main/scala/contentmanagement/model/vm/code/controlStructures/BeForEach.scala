package contentmanagement.model.vm.code.controlStructures

import contentmanagement.model.language.AppLanguage.{Java, JavaScript, Lisp, Python, Rust}
import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.defining.BeDefineVariable
import contentmanagement.model.vm.code.{BeControlStructure, BeExpression}
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import util.CodeStringBuilder

case class BeForEach(loopVariable: BeDefineVariable, iterable: BeExpression, body: BeSequence)
  extends BeControlStructure {

  override def allPossibleBodies: List[BeExpression] = List(body)

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
    val variableName = loopVariable.name.getInLanguage(humanLanguage)
    val iterableString = iterable.getInLanguage(programmingLanguage, humanLanguage).replaceAll("\n", " ")
    val bodyString = body.getInLanguage(programmingLanguage, humanLanguage)

    programmingLanguage match {
      case Python =>
        CodeStringBuilder()
          .appendNextLine(s"for $variableName in $iterableString:")
          .changeIntLevel(1)
          .appendAsLines(bodyString)
          .toString
      case Java =>
        val elementType = languageSpecificType(programmingLanguage, loopVariable.canEvaluateTo)
        CodeStringBuilder()
          .appendNextLine(s"for ($elementType $variableName : $iterableString) {")
          .changeIntLevel(1)
          .appendAsLines(bodyString)
          .changeIntLevel(-1)
          .appendNextLine("}")
          .toString
      case JavaScript =>
        CodeStringBuilder()
          .appendNextLine(s"for (const $variableName of $iterableString) {")
          .changeIntLevel(1)
          .appendAsLines(bodyString)
          .changeIntLevel(-1)
          .appendNextLine("}")
          .toString
      case Rust =>
        CodeStringBuilder()
          .appendNextLine(s"for ${sanitizeRustName(variableName)} in $iterableString {")
          .changeIntLevel(1)
          .appendAsLines(bodyString)
          .changeIntLevel(-1)
          .appendNextLine("}")
          .toString
      case Lisp =>
        CodeStringBuilder(s"(dolist (${variableName.toLowerCase} $iterableString)")
          .changeIntLevel(1)
          .appendAsLines(bodyString)
          .changeIntLevel(-1)
          .appendNextLine(")")
          .toString
      case _ =>
        CodeStringBuilder()
          .appendNextLine(s"FOREACH $variableName IN $iterableString")
          .changeIntLevel(1)
          .appendAsLines(bodyString)
          .changeIntLevel(-1)
          .appendNextLine("END")
          .toString
    }
  }

  override def getSyntaxErrors: Seq[BeInfo] = iterable.getSyntaxErrors ++ body.getSyntaxErrors

  override def createBlock(config: BeDisplayConfig, childPos: BeChildPosition): BeBlock =
    throw new NotImplementedError("Block rendering is not implemented for BeForEach")

  override def getChildren: List[(BeChildRole, BeExpression)] = List(
    (BeChildRole.ExpressionInBody(0), iterable),
    (BeChildRole.BodySequence(0), body)
  )

  private def languageSpecificType(language: ProgrammingLanguage, possibleTypes: Set[BeDataType]): String = {
    val resolvedType = possibleTypes.find(_ != BeDataType.Unit).orElse(possibleTypes.headOption).getOrElse(BeDataType.Unit)
    language match {
      case Java =>
        resolvedType match {
          case BeDataType.Numeric => "double"
          case BeDataType.Boolean => "boolean"
          case BeDataType.String => "String"
          case BeDataType.Date => "java.time.LocalDate"
          case _ => "Object"
        }
      case Rust =>
        resolvedType match {
          case BeDataType.Numeric => "f64"
          case BeDataType.Boolean => "bool"
          case BeDataType.String => "String"
          case BeDataType.Date => "chrono::NaiveDate"
          case _ => "()"
        }
      case _ => resolvedType.toString
    }
  }

  private def sanitizeRustName(name: String): String =
    if (name.nonEmpty && name.head.isUpper) name.head.toLower + name.tail else name
}
