package datastructures.core.vm.code.controlStructures

import datastructures.core.vm.types.BeChildRole.ConditionInControlStructure
import datastructures.core.vm.types.BeScope.InSequenceScope
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import interactionPlugins.blockEnvironment.programming.blockdisplay.control.BeBlockWhile
import util.text.ParenthesesUtils.stripOuterBalancedParens
import datastructures.core.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import datastructures.core.vm.code.{BeControlStructure, BeExpression}
import datastructures.core.vm.io.BeExpressionIO
import datastructures.core.vm.simulation.{BeExpressionExecutor, BeSimulatorConfig, BeSimulatorState}
import datastructures.core.vm.static.BeExpressionStaticInformation
import datastructures.core.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeDataValue, BeInfo, BeScope}
import it.evadid.core.util.CodeStringBuilder

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
case class BeWhile(
                    condition: BeSequence,
                    body: BeSequence)
  extends BeControlStructure {

  override def allPossibleBodies: List[BeExpression] = List(body)

  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {

    override def syntaxErrors: Seq[BeInfo] = BeInfo.typeMismatchInfo("while condition", BeDataType.Boolean, condition.staticInformationExpression.staticType).toList

  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO {
    override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
      val conditionString =
        stripOuterBalancedParens(condition.expressionIO.getInLanguage(programmingLanguage, humanLanguage).replaceAll("\n", ""))
      val bodyString = body.expressionIO.getInLanguage(programmingLanguage, humanLanguage)
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


    override def createBlock(): BeBlock = BeBlockWhile(BeWhile.this)
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
