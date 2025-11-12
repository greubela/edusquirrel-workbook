package contentmanagement.model.vm.code.tree

import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.types.{BeChildPosition, BeDataType}
import interactionPlugins.blockEnvironment.programming.blockdisplay.other.BeBlockPlaceholder

sealed trait BeExpressionNode {

  def childPosition : BeChildPosition

}

case class BeExpressionReference(override val childPosition: BeChildPosition, expr: BeExpression) extends BeExpressionNode{
      
}

case class BeExtensionPoint(isRequired: Boolean, override val childPosition: BeChildPosition, extensionWillBeUsedAsType: BeDataType) extends BeExpressionNode{

}
