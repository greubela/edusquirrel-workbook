package contentmanagement.model.vm.code.controlStructures

import contentmanagement.model.language.AppLanguage.{Java, JavaScript, Lisp, Python, Rust}
import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.{BeControlStructure, BeExpression}
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import util.CodeStringBuilder

case class BeWhile(
                    conditionSource: BeExpression,
                    body: BeSequence)
  extends BeControlStructure {

  override def allPossibleBodies: List[BeExpression] = List(body)

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
    val conditionString = conditionSource.getInLanguage(programmingLanguage, humanLanguage).replaceAll("\n", "")
    val bodyString = body.getInLanguage(programmingLanguage, humanLanguage)
    programmingLanguage match {
      case Python =>
        CodeStringBuilder().appendNextLine(s"while $conditionString:")
          .changeIntLevel(1)
          .appendAsLines(bodyString)
          .toString
      case Java =>
        CodeStringBuilder().appendNextLine(s"while($conditionString){")
          .changeIntLevel(1)
          .appendAsLines(bodyString)
          .changeIntLevel(-1)
          .appendNextLine("}")
          .toString
      case JavaScript =>
        CodeStringBuilder().appendNextLine(s"while ($conditionString) {")
          .changeIntLevel(1)
          .appendAsLines(bodyString)
          .changeIntLevel(-1)
          .appendNextLine("}")
          .toString
      case Rust =>
        CodeStringBuilder().appendNextLine(s"while $conditionString {")
          .changeIntLevel(1)
          .appendAsLines(bodyString)
          .changeIntLevel(-1)
          .appendNextLine("}")
          .toString
      case Lisp =>
        CodeStringBuilder("(loop while " + conditionString)
          .changeIntLevel(1)
          .appendNextLine("do (progn")
          .changeIntLevel(1)
          .appendAsLines(bodyString)
          .changeIntLevel(-1)
          .appendNextLine(")")
          .changeIntLevel(-1)
          .appendNextLine(")")
          .toString
      case _ =>
        CodeStringBuilder().appendNextLine(s"WHILE(")
          .changeIntLevel(1)
          .appendNextLine(conditionString)
          .appendAsLines(bodyString)
          .changeIntLevel(-1)
          .appendNextLine(")")
          .toString
    }
  }


  override def getSyntaxErrors: Seq[BeInfo] = conditionSource.canEvaluateTo.contains(BeDataType.Boolean) match {
    case true => List()
    case false => List(BeInfo(LanguageMap.universalMap("While condition must be able to evaluate to a boolean!"), BeInfo.SyntaxError.TypeMismatch))
  }

  override def createBlock(config: BeDisplayConfig, childPos: BeChildPosition): BeBlock =
    throw new NotImplementedError("Block rendering is not implemented for BeWhile")

  override def getChildren: List[(BeChildRole, BeExpression)] = List(
    (BeChildRole.ConditionInControlStructure, conditionSource),
    (BeChildRole.BodySequence(0), body)
  )
}
