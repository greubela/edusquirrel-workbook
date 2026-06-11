package todomove.datastructures.core.vm.code.controlStructures

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.util.CodeStringBuilder
import todomove.datastructures.core.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import todomove.datastructures.core.vm.code.{BeControlStructure, BeExpression}
import todomove.datastructures.core.vm.controlflow.ControlFlowType
import todomove.datastructures.core.vm.io.{BeExpressionIO, BeExpressionLine, BeCodeLines}
import todomove.datastructures.core.vm.static.BeExpressionStaticInformation
import todomove.datastructures.core.vm.types.*
import todomove.datastructures.core.vm.types.BeChildRole.ConditionInControlStructure
import todomove.datastructures.core.vm.types.BeScope.InSequenceScope

case class BeIfElse(
                     condition: BeSequence,
                     thenBody: BeSequence,
                     elseBody: BeSequence
                   ) extends BeControlStructure {

  private val myRef: BeIfElse = this

  def allPossibleBodies: List[BeExpression] = List(thenBody, elseBody)

  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {
    /*override def staticType: BeDataType = if(staticValue.nonEmpty) staticValue.get.currentType else thenBody.expressionStaticInformation.staticType.

    override def staticValue: Option[BeDataValue] = {
      val condVal = condition.staticInformationIncludingChildren.staticValue
      val thenVal = thenBody.staticInformationIncludingChildren.staticValue
      val elseVal = elseBody.staticInformationIncludingChildren.staticValue
      if (condVal.isEmpty) None
      else if (condVal.get.displayAsString == "true" && thenVal.nonEmpty) thenVal
      else if (condVal.get.displayAsString == "false" && elseVal.nonEmpty) elseVal
      else None
    }*/

    override def syntaxErrors: Seq[BeInfo] = BeInfo.typeMismatchInfo("if/else condition", BeDataType.Boolean, condition.staticInformationExpression.staticType).toList

  }

  override def getChildren(withExtensions: Boolean, myScope: BeScope): List[BeExpressionNode] = {
    List(
      BeExpressionReference(BeChildPosition(ConditionInControlStructure, InSequenceScope(condition, myScope)), condition),
      BeExpressionReference(BeChildPosition(BeChildRole.BodySequence(0), InSequenceScope(thenBody, myScope)), thenBody),
      BeExpressionReference(BeChildPosition(BeChildRole.BodySequence(1), InSequenceScope(elseBody, myScope)), elseBody),
    )
  }
  
  override def expressionIO: BeExpressionIO = new BeExpressionIO {
    
    override def toExpressionLines(myScope: BeScope, myStack: List[ControlFlowType]): BeCodeLines = {
      // BeExpressionLine (nr, expr, role, scope, cfStack)
/*
      val condScope = InSequenceScope(condition, myScope)
      val thenScope = InSequenceScope(thenBody, myScope)
      val elseScope = InSequenceScope(elseBody, myScope)

      val condLines = condition.expressionIO.toExpressionLines(condScope, myStack ++ Some(ControlFlowType.IfElseBranch))

      if (condLines.lines.size != 1) {
        println(s"[WARN] BeIfElse::toExpressionLines - condition does not have exactly 1 line (${condLines.lines.size}) instead!")
      }
      val thenLines = thenBody.expressionIO.toExpressionLines(thenScope, myStack ++ List(ControlFlowType.IfElseBody(false), ControlFlowType.IfElseBody(true)))
      val elseLines = elseBody.expressionIO.toExpressionLines(elseScope, myStack ++ List(ControlFlowType.IfElseBody(true), ControlFlowType.IfElseBody(false)))

      // lineExpression: BeExpression, lineRole: BeChildRole, scope: BeScope, controlFlowStack: List[ControlFlowType]): BeExpressionLines = {

      condLines
        .appendNewLines(thenLines)
*/
      ???
    }

    override def toStringInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, skipUnparsable: Boolean = false): String = {
      val conditionString = condition.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable).replaceAll("\n", "")
      val thenBodyString = thenBody.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable)
      val elseBodyString = elseBody.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable)
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
        case Cpp => {
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
