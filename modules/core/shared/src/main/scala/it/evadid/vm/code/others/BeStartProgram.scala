package it.evadid.vm.code.others

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.vm.io.{BeExpressionStructureInfo, BeSegmentedCodeElement}
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.BeChildRole.BodySequence
import it.evadid.vm.types.BeScope.InSequenceScope
import it.evadid.vm.types.*

case class BeStartProgram(startSequence: Option[BeSequence]) extends BeExpression {

  override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {}

  override lazy val structureInfo: BeExpressionStructureInfo[?] =
    new BeExpressionStructureInfo[BeStartProgram](this) {

      override def withReplacedChildren(newChildren: Map[BeChildRole, BeExpression]): BeStartProgram = {
        val newSequence = newChildren.get(BodySequence(0)).collect { case seq: BeSequence => seq }
        copy(startSequence = newSequence.orElse(startSequence))
      }

      override def toJavaStyleLines(myInfo: BeChildInfo): Seq[BeSegmentedCodeElement] =
        startSequence.toList.flatMap(_.structureInfo.toJavaStyleLines(myInfo))

      override def getChildrenAndExtension(myScope: BeScope): Seq[BeExpressionNode] =
        startSequence.toList.map { seq =>
          BeExpressionReference(BeChildInfo(BodySequence(0), InSequenceScope(seq, myScope)), seq)
        }
    }
}

object BeStartProgram {

  def apply(): BeStartProgram = BeStartProgram(None)

  def apply(startSequence: BeSequence): BeStartProgram = BeStartProgram(Some(startSequence))

}
