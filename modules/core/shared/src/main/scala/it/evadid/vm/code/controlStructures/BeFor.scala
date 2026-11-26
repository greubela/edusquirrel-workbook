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
import upickle.ReadWriter

import upickle.default.*

/**
 * Inclusive counting loop from `start` to `end` in steps of one. `direction` says which way it counts:
 * Snap's `for i = start to end` counts towards `end` either way; Python's `range` and Java's counting
 * `for` count only up (or only down) and make no pass when the bounds point the other way.
 */
case class BeFor(
    variable: BeDefineVariable,
    start: BeExpression,
    end: BeExpression,
    body: BeSequence,
    direction: BeFor.Direction = BeFor.Direction.Either
) extends BeControlStructure derives ReadWriter{

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

object BeFor {
  enum Direction derives ReadWriter {
    case Either, Up, Down
  }
}
