package it.evadid.vm.code.tree

import it.evadid.vm.code.BeExpression
import it.evadid.vm.types.{BeChildPosition, BeDataType}

sealed trait BeExpressionNode {

  def childPosition: BeChildPosition

}

case class BeExpressionReference(override val childPosition: BeChildPosition, expr: BeExpression) extends BeExpressionNode {

}

case class BeExtensionPoint(isRequired: Boolean, override val childPosition: BeChildPosition, extensionWillBeUsedAsType: BeDataType) extends BeExpressionNode {

}
