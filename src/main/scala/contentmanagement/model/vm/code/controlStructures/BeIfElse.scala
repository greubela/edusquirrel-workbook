package contentmanagement.model.vm.code.controlStructures

import contentmanagement.model.language.AppLanguage.*
import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import contentmanagement.model.vm.code.{BeControlStructure, BeExpression}
import contentmanagement.model.vm.types.BeChildRole.ConditionInControlStructure
import contentmanagement.model.vm.types.BeScope.InSequenceScope
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeInfo, BeScope}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import util.CodeStringBuilder

case class BeIfElse(
                     condition: BeSequence,
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
      case JavaScript => {
        CodeStringBuilder().appendNextLine(s"if ($conditionString) {")
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
      case Rust => {
        CodeStringBuilder().appendNextLine(s"if $conditionString {")
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
      case Lisp => {
        val builder = CodeStringBuilder("(if " + conditionString)
          .changeIntLevel(1)
          .appendNextLine("(progn")
          .changeIntLevel(1)
          .appendAsLines(thenBodyString)
          .changeIntLevel(-1)
          .appendNextLine(")")
          .appendNextLine("(progn")
          .changeIntLevel(1)
          .appendAsLines(elseBodyString)
          .changeIntLevel(-1)
          .appendNextLine(")")
          .changeIntLevel(-1)
          .appendNextLine(")")
        builder.toString
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

  override def getSyntaxErrorsOfThisStructure: Seq[BeInfo] = BeInfo.typeMismatchInfo("if/else condition", BeDataType.Boolean, condition.canEvaluateTo).toList


  override def createBlock(): BeBlock =
    throw new NotImplementedError("Block rendering is not implemented for BeIfElse")

  override def getChildren(withExtensions: Boolean, myScope: BeScope): List[BeExpressionNode] = {
    List(
      BeExpressionReference(BeChildPosition(ConditionInControlStructure, InSequenceScope(condition, myScope)), condition),
      BeExpressionReference(BeChildPosition(BeChildRole.BodySequence(0), InSequenceScope(thenBody, myScope)), thenBody),
      BeExpressionReference(BeChildPosition(BeChildRole.BodySequence(1), InSequenceScope(elseBody, myScope)), elseBody),
    )
  }


}
