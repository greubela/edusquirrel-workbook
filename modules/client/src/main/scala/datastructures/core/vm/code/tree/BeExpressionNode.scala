package datastructures.core.vm.code.tree

import datastructures.core.vm.code.BeExpression
import datastructures.core.vm.types.{BeChildPosition, BeDataType}
import interactionPlugins.blockEnvironment.programming.blockdisplay.other.BeBlockPlaceholder
import it.evadid.core.datastructures.tree.nodeImpl.NodeBasedTreePosition

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*

sealed trait BeExpressionNode {

  def childPosition : BeChildPosition

}

case class BeExpressionReference(override val childPosition: BeChildPosition, expr: BeExpression) extends BeExpressionNode{
      
}

case class BeExtensionPoint(isRequired: Boolean, override val childPosition: BeChildPosition, extensionWillBeUsedAsType: BeDataType) extends BeExpressionNode{

}
