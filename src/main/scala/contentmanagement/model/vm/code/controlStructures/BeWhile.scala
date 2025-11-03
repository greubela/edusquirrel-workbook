package contentmanagement.model.vm.code.controlStructures

import contentmanagement.model.language.AppLanguage.{Java, JavaScript, Lisp, Python, Rust}
import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.code.{BeControlStructure, BeExpression}
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeInfo, BeScope}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import util.CodeStringBuilder
import contentmanagement.model.vm.code.tree.*
import contentmanagement.model.vm.types.BeChildRole.ConditionInControlStructure
import contentmanagement.model.vm.types.BeScope.InSequenceScope

case class BeWhile(
                    condition: BeSequence,
                    body: BeSequence)
  extends BeControlStructure {

  override def allPossibleBodies: List[BeExpression] = List(body)

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
    val conditionString = condition.getInLanguage(programmingLanguage, humanLanguage).replaceAll("\n", "")
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
  
  override def getSyntaxErrorsOfThisStructure: Seq[BeInfo] = BeInfo.typeMismatchInfo("while condition", BeDataType.Boolean, condition.canEvaluateTo).toList

  override def createBlock(): BeBlock =
    throw new NotImplementedError("Block rendering is not implemented for BeWhile")

  override def getChildren(withExtensions: Boolean, myScope: BeScope): List[BeExpressionNode] = {
    List(
      BeExpressionReference(BeChildPosition(ConditionInControlStructure, InSequenceScope(condition, myScope)), condition),
      BeExpressionReference(BeChildPosition(BeChildRole.BodySequence(0), InSequenceScope(body, myScope)), body),
    )
  }

  override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = {
    val newCondition = newChildren.collectFirst {
      case (ConditionInControlStructure, seq: BeSequence) => seq
    }.getOrElse(condition)

    val newBody = newChildren.collectFirst {
      case (BeChildRole.BodySequence(0), seq: BeSequence) => seq
    }.getOrElse(body)

    copy(condition = newCondition, body = newBody)
  }
}
