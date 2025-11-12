package contentmanagement.model.vm.code.others

import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.controlStructures.BeSequence
import contentmanagement.model.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.{BodySequence, NoRole}
import contentmanagement.model.vm.types.BeScope.InSequenceScope
import interactionPlugins.blockEnvironment.config.BeTreeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import interactionPlugins.blockEnvironment.programming.blockdisplay.control.BeBlockStarter

case class BeStartProgram(startSequence: Option[BeSequence]) extends BeExpression {

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = startSequence.map(_.getInLanguage(programmingLanguage, humanLanguage)).getOrElse("")

  override def hasThisExpressionSideEffects: Boolean = false

  override def getSyntaxErrorsOfThisStructure: Seq[BeInfo] = List()

  override def canEvaluateTo: BeDataType = BeDataType.Unit

  override def createBlock(): BeBlock =

    BeBlockStarter()

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