package it.evadid.vm.code.controlStructures

import it.evadid.vm.code.abstractions.{BeControlStructure, BeExpression}
import it.evadid.vm.code.defining.BeDefineVariable
import it.evadid.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.vm.controlflow.ControlFlowType.{ControlFlowUp, RepeatBranch, RepeatUnion}
import it.evadid.vm.io.BeSegmentedCodeElement.BeControlFlowLine
import it.evadid.vm.io.{BeExpressionStructureInfo, BeSegmentedCodeElement}
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.*
import it.evadid.vm.types.BeScope.InSequenceScope

/**
 * Inclusive numeric for-loop (`for i = start to end` in Snap `doFor`).
 * Python prints as `for i in range(start, end + 1):`.
 */
case class BeFor(
    variable: BeDefineVariable,
    start: BeExpression,
    end: BeExpression,
    body: BeSequence
) extends BeControlStructure {

  override def allPossibleBodies: Seq[BeExpression] = List(body)

  override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {}

  override lazy val structureInfo: BeExpressionStructureInfo[?] = new BeExpressionStructureInfo(this) {

    override def getChildrenAndExtension(myScope: BeScope): List[BeExpressionNode] =
      List(
        BeExpressionReference(BeChildInfo(BeChildRole.ExpressionInSequence(0), myScope), start),
        BeExpressionReference(BeChildInfo(BeChildRole.ExpressionInSequence(1), myScope), end),
        BeExpressionReference(BeChildInfo(BeChildRole.BodySequence(0), InSequenceScope(body, myScope)), body)
      )

    override def withReplacedChildren(newChildren: Map[BeChildRole, BeExpression]): BeFor = {
      val newStart = newChildren.getOrElse(BeChildRole.ExpressionInSequence(0), start)
      val newEnd = newChildren.getOrElse(BeChildRole.ExpressionInSequence(1), end)
      val newBody = newChildren.collectFirst {
        case (BeChildRole.BodySequence(0), seq: BeSequence) => seq
      }.getOrElse(body)
      copy(start = newStart, end = newEnd, body = newBody)
    }

    override def toJavaStyleLines(myInfo: BeChildInfo): Seq[BeSegmentedCodeElement] =
      List(
        BeControlFlowLine(RepeatBranch),
        getChildrenAsReference(myInfo.myScope).last.toSegment(Some(ControlFlowUp)),
        BeControlFlowLine(RepeatUnion)
      )
  }
}
