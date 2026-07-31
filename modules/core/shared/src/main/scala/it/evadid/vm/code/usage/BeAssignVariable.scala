package it.evadid.vm.code.usage

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.defining.BeDefineVariable
import it.evadid.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.vm.controlflow.ControlFlowType.ControlFlowDown
import it.evadid.vm.io.{BeExpressionStructureInfo, BeSegmentedCodeElement}
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.*

case class BeAssignVariable(target: BeDefineVariable, value: BeExpression) extends BeExpression {

  override lazy val structureInfo: BeExpressionStructureInfo[?] = new BeExpressionStructureInfo[BeAssignVariable](this) {
    override def withReplacedChildren(newChildren: Map[BeChildRole, BeExpression]): BeAssignVariable = {
      val replacement = newChildren.get(BeChildRole.ValueInAssignment)
        .orElse(newChildren.get(BeChildRole.ValueForVariable(target)))
        .orElse(newChildren.collectFirst { case (BeChildRole.ExpressionInSequence(_), expression) => expression })
      replacement.map(expression => copy(value = expression)).getOrElse(BeAssignVariable.this)
    }

    override def toJavaStyleLines(myInfo: BeChildInfo): Seq[BeSegmentedCodeElement] = asExpressionLine(ControlFlowDown, myInfo)

    override def getChildrenAndExtension(myScope: BeScope): Seq[BeExpressionNode] = List(
      BeExpressionReference(BeChildInfo(BeChildRole.ValueInAssignment, myScope), value)
    )
  }

  //private val assignPossible: BeDataTypeAssigningPossible = target.variableType.canTakeValuesFrom(value.possibleStaticTypes)

  override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {

    override def syntaxErrors: Seq[BeInfo] = BeInfo.typeMismatchInfo("value " + value + " for assigning", target.staticInformationExpression.staticType, value.staticInformationExpression.staticType).toList

    override def hasSideEffects: Boolean = true
  }


  /*
    override def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode] = List(
      BeExpressionReference(BeChildInfo(ValueInAssignment, parentScope), value)
    )*/

  override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = {
    val replacement = newChildren.collectFirst {
      case (BeChildRole.ValueForVariable(variable), expr) if variable == target => expr
      case (BeChildRole.ExpressionInSequence(_), expr) => expr
    }

    replacement.map(expr => copy(value = expr)).getOrElse(BeAssignVariable.this)
  }

}
