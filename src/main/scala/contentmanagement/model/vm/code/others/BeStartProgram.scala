package contentmanagement.model.vm.code.others

import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.controlStructures.BeSequence
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.NoRole
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.parents.BeBlockStarter

case class BeStartProgram(startSequence: Option[BeSequence]) extends BeExpression {

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = startSequence.map(_.getInLanguage(programmingLanguage, humanLanguage)).getOrElse("")

  override def hasThisExpressionSideEffects: Boolean = false

  override def getSyntaxErrors: Seq[BeInfo] = List()

  override def canEvaluateTo: Set[BeDataType] = Set(BeDataType.Unit)

  override def createBlock(config: BeDisplayConfig, childPos: BeChildPosition): BeBlock = {
    BeBlockStarter(childPos)
  }

  def createBlock(config: BeDisplayConfig): BeBlock
  = createBlock(config, BeChildPosition(NodeBasedTreePosition.root, BeChildRole.NoRole, BeScope.GlobalScope()))

  override def getChildren: List[(BeChildRole, BeExpression)] = startSequence.map(seq => (BeChildRole.BodySequence(0), seq)).toList

}

object BeStartProgram {

  def apply(): BeStartProgram = BeStartProgram(None)

  def apply(startSequence: BeSequence): BeStartProgram = BeStartProgram(Some(startSequence))

}