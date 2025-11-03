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
    val hasElseBody = elseBody.body.nonEmpty
    programmingLanguage match {
      case Python => {
        val builder = CodeStringBuilder().appendNextLine(s"if $conditionString:")
          .changeIntLevel(1)
          .appendAsLines(thenBodyString)
          .changeIntLevel(-1)
        if (hasElseBody) {
          builder
            .appendNextLine(s"else:")
            .changeIntLevel(1)
            .appendAsLines(elseBodyString)
            .changeIntLevel(-1)
        }
        builder
          .appendNextLine(s"")
          .toString
      }
      case Java => {
        val builder = CodeStringBuilder().appendNextLine(s"if($conditionString){")
          .changeIntLevel(1)
          .appendAsLines(thenBodyString)
          .changeIntLevel(-1)
        if (hasElseBody) {
          builder
            .appendNextLine("} else {")
            .changeIntLevel(1)
            .appendAsLines(elseBodyString)
            .changeIntLevel(-1)
            .appendNextLine("}")
        } else {
          builder.appendNextLine("}")
        }
        builder.toString
      }
      case JavaScript => {
        val builder = CodeStringBuilder().appendNextLine(s"if ($conditionString) {")
          .changeIntLevel(1)
          .appendAsLines(thenBodyString)
          .changeIntLevel(-1)
        if (hasElseBody) {
          builder
            .appendNextLine("} else {")
            .changeIntLevel(1)
            .appendAsLines(elseBodyString)
            .changeIntLevel(-1)
            .appendNextLine("}")
        } else {
          builder.appendNextLine("}")
        }
        builder.toString
      }
      case Rust => {
        val builder = CodeStringBuilder().appendNextLine(s"if $conditionString {")
          .changeIntLevel(1)
          .appendAsLines(thenBodyString)
          .changeIntLevel(-1)
        if (hasElseBody) {
          builder
            .appendNextLine("} else {")
            .changeIntLevel(1)
            .appendAsLines(elseBodyString)
            .changeIntLevel(-1)
            .appendNextLine("}")
        } else {
          builder.appendNextLine("}")
        }
        builder.toString
      }
      case Lisp => {
        val builder = CodeStringBuilder("(if " + conditionString)
          .changeIntLevel(1)
          .appendNextLine("(progn")
          .changeIntLevel(1)
          .appendAsLines(thenBodyString)
          .changeIntLevel(-1)
          .appendNextLine(")")
        if (hasElseBody) {
          builder
            .appendNextLine("(progn")
            .changeIntLevel(1)
            .appendAsLines(elseBodyString)
            .changeIntLevel(-1)
            .appendNextLine(")")
        }
        builder
          .changeIntLevel(-1)
          .appendNextLine(")")
          .toString
      }
      case _ => {
        val builder = CodeStringBuilder().appendNextLine(s"IF/ELSE(")
          .changeIntLevel(1)
          .appendNextLine(s"$conditionString,")
          .appendAsLines(thenBodyString)
        if (hasElseBody) builder.appendAsLines(elseBodyString)
        builder
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

  override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = {
    val newCondition = newChildren.collectFirst {
      case (ConditionInControlStructure, seq: BeSequence) => seq
    }.getOrElse(condition)

    val newThenBody = newChildren.collectFirst {
      case (BeChildRole.BodySequence(0), seq: BeSequence) => seq
    }.getOrElse(thenBody)

    val newElseBody = newChildren.collectFirst {
      case (BeChildRole.BodySequence(1), seq: BeSequence) => seq
    }.getOrElse(elseBody)

    copy(condition = newCondition, thenBody = newThenBody, elseBody = newElseBody)
  }

}
