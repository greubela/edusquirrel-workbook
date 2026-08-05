package it.evadid.vm.code.others

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.vm.controlflow.ControlFlowType.ControlFlowJump
import it.evadid.vm.io.{BeExpressionStructureInfo, BeSegmentedCodeElement}
import it.evadid.vm.types.*
import it.evadid.vm.types.BeChildRole.ReturnValue

case class BeReturn(value: Option[BeExpression]) extends BeExpression {

  override lazy val structureInfo: BeExpressionStructureInfo[?] =
    new BeExpressionStructureInfo[BeReturn](this) {

      override def withReplacedChildren(newChildren: Map[BeChildRole, BeExpression]): BeReturn = {
        val replacement = newChildren.collectFirst { case (ReturnValue(_), expr) => expr }
        replacement.map(expr => BeReturn.this.copy(value = Some(expr))).getOrElse(BeReturn.this)
      }

      override def toJavaStyleLines(myInfo: BeChildInfo): Seq[BeSegmentedCodeElement] = {
        asExpressionLine(ControlFlowJump, myInfo)
      }

      override def getChildrenAndExtension(myScope: BeScope): Seq[BeExpressionNode] = {
        if (value.isEmpty) List() else
          List(BeExpressionReference(BeChildInfo(ReturnValue(0), myScope), value.get))
      }
    }
}
