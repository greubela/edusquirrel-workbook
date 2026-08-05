package it.evadid.vm.code.controlStructures

import it.evadid.vm.code.abstractions.{BeControlStructure, BeExpression}
import it.evadid.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.vm.controlflow.ControlFlowType.{ControlFlowUp, RepeatBranch, RepeatUnion}
import it.evadid.vm.io.BeSegmentedCodeElement.BeControlFlowLine
import it.evadid.vm.io.{BeExpressionStructureInfo, BeSegmentedCodeElement}
import it.evadid.vm.simulation.*
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.*
import it.evadid.vm.types.BeChildRole.ConditionInControlStructure
import it.evadid.vm.types.BeScope.InSequenceScope

case class BeWhile(
                    condition: BeSequence,
                    body: BeSequence)
  extends BeControlStructure {

  override def allPossibleBodies: Seq[BeExpression] = List(body)

  override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {
    override def syntaxErrors: Seq[BeInfo] = BeInfo.typeMismatchInfo("while condition", BeDataType.Boolean, condition.staticInformationExpression.staticType).toList
  }

  override lazy val structureInfo: BeExpressionStructureInfo[?] = new BeExpressionStructureInfo[BeWhile](this) {
    override def withReplacedChildren(newChildren: Map[BeChildRole, BeExpression]): BeWhile = {
      val newCondition = newChildren.get(ConditionInControlStructure).collect { case sequence: BeSequence => sequence }.getOrElse(condition)
      val newBody = newChildren.get(BeChildRole.BodySequence(0)).collect { case sequence: BeSequence => sequence }.getOrElse(body)
      copy(condition = newCondition, body = newBody)
    }

    override def toJavaStyleLines(myInfo: BeChildInfo): Seq[BeSegmentedCodeElement] = List(
      BeControlFlowLine(RepeatBranch),
      getChildrenAsReference(myInfo.myScope).head.toSegment(Some(ControlFlowUp)),
      BeControlFlowLine(RepeatUnion)
    )

    override def getChildrenAndExtension(myScope: BeScope): Seq[BeExpressionNode] = List(
      BeExpressionReference(BeChildInfo(ConditionInControlStructure, InSequenceScope(condition, myScope)), condition),
      BeExpressionReference(BeChildInfo(BeChildRole.BodySequence(0), InSequenceScope(body, myScope)), body)
    )
  }


  override def expressionExecutor(simulatorConfig: BeSimulatorConfig, stateBeforeExecution: BeSimulatorState): BeExpressionExecutor = new BeExpressionExecutor(simulatorConfig, stateBeforeExecution, this) {
    override protected def childExpressionsToExecute(stateBeforeExecution: BeSimulatorState): List[BeExpression] = ???

    override protected def applySideEffectsOfThisBlock(stateBeforeExecution: BeSimulatorState, childrenResults: Seq[(BeSimulatorState, BeDataValue)]): BeSimulatorState = ???

    override protected def executeThisBlockInSimulatorAndGetValue(stateBeforeExecution: BeSimulatorState, childrenResults: Seq[(BeSimulatorState, BeDataValue)]): (BeSimulatorState, BeDataValue) = ???
  }

  /*
    override def getChildren(withExtensions: Boolean, myScope: BeScope): List[BeExpressionNode] = {
      List(
        BeExpressionReference(BeChildInfo(ConditionInControlStructure, InSequenceScope(condition, myScope)), condition),
        BeExpressionReference(BeChildInfo(BeChildRole.BodySequence(0), InSequenceScope(body, myScope)), body),
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
   */
}
