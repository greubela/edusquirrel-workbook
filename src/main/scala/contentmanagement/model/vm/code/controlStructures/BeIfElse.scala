package contentmanagement.model.vm.code.controlStructures

import contentmanagement.model.language.AppLanguage.*
import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import contentmanagement.model.vm.code.{BeControlStructure, BeExpression}
import contentmanagement.model.vm.io.BeExpressionIO
import contentmanagement.model.vm.simulation.{BeExpressionExecutor, BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.static.BeExpressionStaticInformation
import contentmanagement.model.vm.types.BeChildRole.ConditionInControlStructure
import contentmanagement.model.vm.types.BeScope.InSequenceScope
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.BeTreeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import interactionPlugins.blockEnvironment.programming.blockdisplay.control.BeBlockIfElse
import util.CodeStringBuilder
import contentmanagement.model.vm.types.*

case class BeIfElse(
                     condition: BeSequence,
                     thenBody: BeSequence,
                     elseBody: BeSequence
                   ) extends BeControlStructure {

  private val myRef: BeIfElse = this

  def allPossibleBodies: List[BeExpression] = List(thenBody, elseBody)

  override def getChildren(withExtensions: Boolean, myScope: BeScope): List[BeExpressionNode] = {
    List(
      BeExpressionReference(BeChildPosition(ConditionInControlStructure, InSequenceScope(condition, myScope)), condition),
      BeExpressionReference(BeChildPosition(BeChildRole.BodySequence(0), InSequenceScope(thenBody, myScope)), thenBody),
      BeExpressionReference(BeChildPosition(BeChildRole.BodySequence(1), InSequenceScope(elseBody, myScope)), elseBody),
    )
  }


  override def expressionStaticInformation: BeExpressionStaticInformation = new BeExpressionStaticInformation {
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
    override def staticType: BeDataType = BeDataType.Unit

    override def staticValue: Option[BeDataValue] = Some(BeDataValueUnit())

    override def syntaxErrors: Seq[BeInfo] = BeInfo.typeMismatchInfo("if/else condition", BeDataType.Boolean, condition.expressionStaticInformation.staticType).toList

    override def hasSideEffects: Boolean = false
  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO {
    override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
      val conditionString = condition.expressionIO.getInLanguage(programmingLanguage, humanLanguage).replaceAll("\n", "")
      val thenBodyString = thenBody.expressionIO.getInLanguage(programmingLanguage, humanLanguage)
      val elseBodyString = elseBody.expressionIO.getInLanguage(programmingLanguage, humanLanguage)
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

    override def createBlock(): BeBlock = new BeBlockIfElse(myRef)
  }


}
