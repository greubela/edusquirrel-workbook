package it.evadid.vm.code.controlStructures

import it.evadid.vm.code.abstractions.{BeControlStructure, BeExpression}
import it.evadid.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.vm.controlflow.ControlFlowType.{ControlFlowDown, IfElseBranch, IfElseCross, IfElseUnion}
import it.evadid.vm.io.BeSegmentedCodeElement.BeControlFlowLine
import it.evadid.vm.io.{BeExpressionStructureInfo, BeSegmentedCodeElement}
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.*
import it.evadid.vm.types.BeChildRole.ConditionInControlStructure
import it.evadid.vm.types.BeScope.InSequenceScope

case class BeIfElse(
                     condition: BeSequence,
                     thenBody: BeSequence,
                     elseBody: BeSequence
                   ) extends BeControlStructure {

  private val myRef: BeIfElse = this

  def allPossibleBodies: Seq[BeExpression] = List(thenBody, elseBody)

  override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {
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

  override lazy val structureInfo: BeExpressionStructureInfo[?] = new BeExpressionStructureInfo[BeIfElse](this) {

    override def withReplacedChildren(newChildren: Map[BeChildRole, BeExpression]): BeIfElse = {
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

    override def toJavaStyleLines(myInfo: BeChildInfo): Seq[BeSegmentedCodeElement] = {
      val childrenRef = getChildrenAsReference(myInfo.myScope)
      val ifThen: Seq[BeSegmentedCodeElement] = List(
        BeControlFlowLine(IfElseBranch),
        childrenRef(0).toSegment(Some(ControlFlowDown))
      )
      val elseBlock: Seq[BeSegmentedCodeElement] = List(
        BeControlFlowLine(IfElseCross),
        childrenRef(1).toSegment(Some(ControlFlowDown))
      )
      val finish: Seq[BeSegmentedCodeElement] = List(
        BeControlFlowLine(IfElseUnion)
      )
      if (elseBody.body.nonEmpty) ifThen ++ elseBlock ++ finish
      else ifThen ++ finish
    }


    override def getChildrenAndExtension(myScope: BeScope): Seq[BeExpressionNode] = List(
      BeExpressionReference(BeChildInfo(ConditionInControlStructure, InSequenceScope(condition, myScope)), condition),
      BeExpressionReference(BeChildInfo(BeChildRole.BodySequence(0), InSequenceScope(thenBody, myScope)), thenBody),
      BeExpressionReference(BeChildInfo(BeChildRole.BodySequence(1), InSequenceScope(elseBody, myScope)), elseBody),
    )

  }
}
