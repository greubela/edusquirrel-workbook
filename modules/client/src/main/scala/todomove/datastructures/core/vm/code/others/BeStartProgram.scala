package todomove.datastructures.core.vm.code.others

import todomove.datastructures.core.vm.types.BeChildRole.BodySequence
import todomove.datastructures.core.vm.types.BeScope.InSequenceScope

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.control.BeBlockStarter
import todomove.datastructures.core.vm.code.BeExpression
import todomove.datastructures.core.vm.code.controlStructures.BeSequence
import todomove.datastructures.core.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import todomove.datastructures.core.vm.io.BeExpressionIO
import todomove.datastructures.core.vm.static.BeExpressionStaticInformation
import todomove.datastructures.core.vm.types.{BeChildPosition, BeChildRole, BeScope}

case class BeStartProgram(startSequence: Option[BeSequence]) extends BeExpression {


  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {

  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO {
    override def toStringInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, skipUnparsable: Boolean = false): String =
      startSequence.map(_.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable)).getOrElse("")

    override def toBlock(): BeBlock = BeBlockStarter()
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