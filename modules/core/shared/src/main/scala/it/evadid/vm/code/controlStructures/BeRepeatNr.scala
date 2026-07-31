package it.evadid.vm.code.controlStructures

import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.vm.code.abstractions.{BeControlStructure, BeExpression}
import it.evadid.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.vm.controlflow.ControlFlowType.{ControlFlowUp, RepeatBranch}
import it.evadid.vm.io.BeSegmentedCodeElement.BeControlFlowLine
import it.evadid.vm.io.{BeExpressionStructureInfo, BeSegmentedCodeElement}
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.*
import it.evadid.vm.types.BeScope.InSequenceScope

case class BeRepeatNr(amount: Int, body: BeSequence) extends BeControlStructure {

  override def allPossibleBodies: Seq[BeExpression] = List(body)

  override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {

    override def syntaxErrors: Seq[BeInfo] = {
      if (amount < 0) List(BeInfo(LanguageMap.universalMap("repeat count must be zero or positive"), BeInfo.SyntaxError.InvalidLiteralValue))
      else List()
    }


  }

  override lazy val structureInfo: BeExpressionStructureInfo[?] = new BeExpressionStructureInfo(this) {

    override def getChildrenAndExtension(myScope: BeScope): List[BeExpressionNode] =
      List(BeExpressionReference(BeChildInfo(BeChildRole.BodySequence(0), InSequenceScope(body, myScope)), body))

    override def withReplacedChildren(newChildren: Map[BeChildRole, BeExpression]): BeRepeatNr = {
      val newBody = newChildren.collectFirst {
        case (BeChildRole.BodySequence(0), seq: BeSequence) => seq
      }.getOrElse(body)
      copy(body = newBody)
    }

    override def toJavaStyleLines(myInfo: BeChildInfo): Seq[BeSegmentedCodeElement] = {
      List(
        BeControlFlowLine(RepeatBranch),
        getChildrenAsReference(myInfo.myScope).head.toSegment(Some(ControlFlowUp))
      )
    }

  }

}
