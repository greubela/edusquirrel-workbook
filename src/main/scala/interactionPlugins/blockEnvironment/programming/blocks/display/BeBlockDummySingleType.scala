package interactionPlugins.blockEnvironment.programming.blocks.display

import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import interactionPlugins.blockEnvironment.programming.BeDataType
import interactionPlugins.blockEnvironment.programming.connection.BeConnectionRole
import interactionPlugins.blockEnvironment.programming.rendering.BeBlockDisplayManager

case class BeBlockDummySingleType(acceptsType: BeDataType, roleInParent: BeConnectionRole, addAtPosition: NodeBasedTreePosition) extends BeBlockDisplay {

  override def mayEvaluateTo: Set[BeDataType] = Set(acceptsType)

  override def layoutManager: BeBlockDisplayManager = {
    println("BeBlockDummySingleType::layoutManager not implemented yet!")
    ???
  }
}
