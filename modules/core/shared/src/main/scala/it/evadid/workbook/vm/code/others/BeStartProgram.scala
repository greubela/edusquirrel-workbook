package it.evadid.workbook.vm.code.others

import it.evadid.workbook.vm.types.BeChildRole.BodySequence
import it.evadid.workbook.vm.types.BeScope.InSequenceScope

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.workbook.vm.code.BeExpression
import it.evadid.workbook.vm.code.controlStructures.BeSequence
import it.evadid.workbook.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.workbook.vm.io.BeExpressionIO
import it.evadid.workbook.vm.static.BeExpressionStaticInformation
import it.evadid.workbook.vm.types.{BeChildPosition, BeChildRole, BeScope}

case class BeStartProgram(startSequence: Option[BeSequence]) extends BeExpression {


  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {

  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO {
    override def toStringInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, skipUnparsable: Boolean = false): String =
      startSequence.map(_.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable)).getOrElse("")

  }


  override def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode] = startSequence.map(seq =>
    BeExpressionReference(BeChildPosition(BodySequence(0), InSequenceScope(seq, parentScope)), seq)
  ).toList

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
