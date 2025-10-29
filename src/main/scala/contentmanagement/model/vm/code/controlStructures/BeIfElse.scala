package contentmanagement.model.vm.code.controlStructures

import contentmanagement.model.language.AppLanguage.{Java, Python}
import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.usage.BeUseValue
import contentmanagement.model.vm.code.{BeControlStructure, BeExpression}
import contentmanagement.model.vm.simulation.BeSimulatorState
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import util.CodeStringBuilder

case class BeIfElse(
                     condition: BeExpression,
                     thenBody: BeSequence,
                     elseBody: BeSequence
                   ) extends BeControlStructure {

  def allPossibleBodies: List[BeExpression] = List(thenBody, elseBody)

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
    val conditionString = condition.getInLanguage(programmingLanguage, humanLanguage).replaceAll("\n", "")
    val thenBodyString = thenBody.getInLanguage(programmingLanguage, humanLanguage)
    val elseBodyString = elseBody.getInLanguage(programmingLanguage, humanLanguage)
    programmingLanguage match {
      case Python => {
        CodeStringBuilder().appendNextLine(s"if $conditionString:")
          .changeIntLevel(1)
          .appendAsLines(thenBodyString)
          .changeIntLevel(-1)
          .appendNextLine(s"else:")
          .changeIntLevel(1)
          .appendAsLines(elseBodyString)
          .changeIntLevel(-1)
          .appendNextLine(s"")
          .toString
      }
      case Java => {
        CodeStringBuilder().appendNextLine(s"if($conditionString){")
          .changeIntLevel(1)
          .appendAsLines(thenBodyString)
          .changeIntLevel(-1)
          .appendNextLine("} else {")
          .changeIntLevel(1)
          .appendAsLines(elseBodyString)
          .changeIntLevel(-1)
          .appendNextLine("}")
          .toString
      }
      case _ => {
        CodeStringBuilder().appendNextLine(s"IF/ELSE(")
          .changeIntLevel(1)
          .appendNextLine(s"$conditionString,")
          .appendAsLines(thenBodyString)
          .appendAsLines(elseBodyString)
          .changeIntLevel(-1)
          .appendNextLine(")")
          .toString
      }
    }
  }
  
  override def getSyntaxErrors: Seq[BeInfo] = condition.canEvaluateTo.contains(BeDataType.Boolean) match {
    case true => List()
    case false => List(BeInfo(LanguageMap.universalMap("if/else condition must be able to evaluate to a boolean!"), BeInfo.SyntaxError.TypeMismatch))
  }

  override def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock = ???

  override def getChildren: List[(BeChildRole, BeExpression)] = {
    List(
      (BeChildRole.ConditionInControlStructure, condition),
      (BeChildRole.BodySequence(0), thenBody),
      (BeChildRole.BodySequence(1), elseBody)
    )
  }


}
