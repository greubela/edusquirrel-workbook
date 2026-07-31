package it.evadid.vm.code.others

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.vm.io.BeExpressionStructureInfo
import it.evadid.vm.naming.CodeRepresentationConfig
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.BeChildRole.BodySequence
import it.evadid.vm.types.BeScope.InSequenceScope
import it.evadid.vm.types.*

case class BeStartProgram(startSequence: Option[BeSequence]) extends BeExpression {


  override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {

  }

/*
  override def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode] = startSequence.map(seq =>
    BeExpressionReference(BeChildInfo(BodySequence(0), InSequenceScope(seq, parentScope)), seq)
  ).toList*/

  override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = {
    val newSequence = newChildren.collectFirst {
      case (BodySequence(0), seq: BeSequence) => seq
    }

    copy(startSequence = newSequence.orElse(startSequence))
  }

}

object BeStartProgram {

  def apply(): BeStartProgram = BeStartProgram(None)

  def apply(startSequence: BeSequence): BeStartProgram = BeStartProgram(Some(startSequence))

}
