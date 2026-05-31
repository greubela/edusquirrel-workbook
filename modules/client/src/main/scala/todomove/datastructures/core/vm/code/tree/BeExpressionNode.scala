package todomove.datastructures.core.vm.code.tree

import it.evadid.core.datastructures.tree.nodeImpl.NodeBasedTreePosition

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.other.BeBlockPlaceholder
import todomove.datastructures.core.vm.code.BeExpression
import todomove.datastructures.core.vm.types.{BeChildPosition, BeDataType}

sealed trait BeExpressionNode {

  def childPosition : BeChildPosition

}

case class BeExpressionReference(override val childPosition: BeChildPosition, expr: BeExpression) extends BeExpressionNode{
      
}

case class BeExtensionPoint(isRequired: Boolean, override val childPosition: BeChildPosition, extensionWillBeUsedAsType: BeDataType) extends BeExpressionNode{

}
