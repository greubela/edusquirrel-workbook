package it.evadid.vm.code.tree

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.controlflow.ControlFlowType
import it.evadid.vm.io.BeSegmentedCodeElement.BeSegment
import it.evadid.vm.types.{BeChildInfo, BeDataType}

sealed trait BeExpressionNode {

  def childInfo: BeChildInfo

}

case class BeExpressionReference(override val childInfo: BeChildInfo, expr: BeExpression) extends BeExpressionNode {
  def toSegment(addToCfStack: Option[ControlFlowType]): BeSegment = {
    BeSegment(
      addToCfStack,
      childInfo,
      expr.structureInfo.toJavaStyleLines(childInfo),
    )
  }
}

case class BeExtensionPoint(isRequired: Boolean, override val childInfo: BeChildInfo, extensionWillBeUsedAsType: BeDataType) extends BeExpressionNode {

}
