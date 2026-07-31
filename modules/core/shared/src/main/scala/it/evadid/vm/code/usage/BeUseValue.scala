package it.evadid.vm.code.usage

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.defining.BeDefineVariable
import it.evadid.vm.code.tree.BeExpressionNode
import it.evadid.vm.controlflow.ControlFlowType.ControlFlowDown
import it.evadid.vm.io.{BeExpressionStructureInfo, BeSegmentedCodeElement}
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.*

/* necessary to distinguish between "variable is used in a reading context for assigning" or "variable is used in a reading context as parameter"...
 because the VALUE and VARIABLE might be the same... but the USAGE is not (for comparison reasons)
 also... necessary to get from the definevariable to the usevariable
enables to not implement everything in the value (which would be problematic for comparison reasons - 100 and 100 are the same, 100 at this position as parameter and 100 at that position as var def are not the same
*/

case class BeUseValue(value: BeDataValue, contextIfKnown: Option[BeDefineVariable]) extends BeExpression {

  override lazy val structureInfo: BeExpressionStructureInfo[?] = new BeExpressionStructureInfo[BeUseValue](this) {
    override def withReplacedChildren(newChildren: Map[BeChildRole, BeExpression]): BeUseValue = BeUseValue.this
    override def toJavaStyleLines(myInfo: BeChildInfo): Seq[BeSegmentedCodeElement] = asExpressionLine(ControlFlowDown, myInfo)
    override def getChildrenAndExtension(myScope: BeScope): Seq[BeExpressionNode] = Seq.empty
  }

  override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {
    override def staticType: BeDataType = value.currentType

    override def staticValue: Option[BeDataValue] = Some(value)

  }

}
