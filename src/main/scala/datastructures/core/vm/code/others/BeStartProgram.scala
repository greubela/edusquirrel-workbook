package datastructures.core.vm.code.others

import datastructures.core.vm.types.BeChildRole.BodySequence
import datastructures.core.vm.types.BeScope.InSequenceScope
import datastructures.core.language.{HumanLanguage, ProgrammingLanguage}
import datastructures.core.vm.code.BeExpression
import datastructures.core.vm.code.controlStructures.BeSequence
import datastructures.core.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import datastructures.core.vm.io.BeExpressionIO
import datastructures.core.vm.static.BeExpressionStaticInformation
import datastructures.core.vm.types.{BeChildPosition, BeChildRole, BeScope}
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import interactionPlugins.blockEnvironment.programming.blockdisplay.control.BeBlockStarter

case class BeStartProgram(startSequence: Option[BeSequence]) extends BeExpression {


  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {

  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO {
    override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String =
      startSequence.map(_.expressionIO.getInLanguage(programmingLanguage, humanLanguage)).getOrElse("")

    override def createBlock(): BeBlock = BeBlockStarter()
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