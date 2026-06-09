package todomove.datastructures.core.vm.code.controlStructures

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.util.CodeStringBuilder
import it.evadid.homepage.util.text.ParenthesesUtils.stripOuterBalancedParens
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.control.BeBlockWhile
import todomove.datastructures.core.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import todomove.datastructures.core.vm.code.{BeControlStructure, BeExpression}
import todomove.datastructures.core.vm.io.BeExpressionIO
import todomove.datastructures.core.vm.simulation.{BeExpressionExecutor, BeSimulatorConfig, BeSimulatorState}
import todomove.datastructures.core.vm.static.BeExpressionStaticInformation
import todomove.datastructures.core.vm.types.*
import todomove.datastructures.core.vm.types.BeChildRole.ConditionInControlStructure
import todomove.datastructures.core.vm.types.BeScope.InSequenceScope

case class BeWhile(
                    condition: BeSequence,
                    body: BeSequence)
  extends BeControlStructure {

  override def allPossibleBodies: List[BeExpression] = List(body)

  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {

    override def syntaxErrors: Seq[BeInfo] = BeInfo.typeMismatchInfo("while condition", BeDataType.Boolean, condition.staticInformationExpression.staticType).toList

  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO {
    override def toStringInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, skipUnparsable: Boolean = false): String = {
      val conditionString =
        stripOuterBalancedParens(condition.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable).replaceAll("\n", ""))
      val bodyString = body.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable)
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
        case Cpp =>
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


    override def toBlock(): BeBlock = BeBlockWhile(BeWhile.this)
  }

  override def expressionExecutor(simulatorConfig: BeSimulatorConfig, stateBeforeExecution: BeSimulatorState): BeExpressionExecutor = new BeExpressionExecutor(simulatorConfig, stateBeforeExecution, this) {
    override protected def childExpressionsToExecute(stateBeforeExecution: BeSimulatorState): List[BeExpression] = ???

    override protected def applySideEffectsOfThisBlock(stateBeforeExecution: BeSimulatorState, childrenResults: List[(BeSimulatorState, BeDataValue)]): BeSimulatorState = ???

    override protected def executeThisBlockInSimulatorAndGetValue(stateBeforeExecution: BeSimulatorState, childrenResults: List[(BeSimulatorState, BeDataValue)]): (BeSimulatorState, BeDataValue) = ???
  }


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
