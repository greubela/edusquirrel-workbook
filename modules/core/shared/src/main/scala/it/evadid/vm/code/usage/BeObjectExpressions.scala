package it.evadid.vm.code.usage

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.defining.BeDefineVariable
import it.evadid.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.vm.controlflow.ControlFlowType.ControlFlowDown
import it.evadid.vm.io.{BeExpressionStructureInfo, BeSegmentedCodeElement}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.simulation.*
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.*
import upickle.default.*

/** Object expressions have no language-specific receiver spelling or source payload. */
trait BeObjectExpression extends BeExpression {
  protected def children: Seq[(BeChildRole, BeExpression)]
  protected def replace(children: Map[BeChildRole, BeExpression]): BeObjectExpression
  override lazy val structureInfo: BeExpressionStructureInfo[?] = new BeExpressionStructureInfo[BeObjectExpression](this) {
    override def withReplacedChildren(newChildren: Map[BeChildRole, BeExpression]): BeObjectExpression = replace(newChildren)
    override def toJavaStyleLines(info: BeChildInfo): Seq[BeSegmentedCodeElement] = asExpressionLine(ControlFlowDown(), info)
    override def getChildrenAndExtension(scope: BeScope): Seq[BeExpressionNode] = children.map { (role, expression) =>
      BeExpressionReference(BeChildInfo(role, scope), expression)
    }
  }
  override def expressionExecutor(config: BeSimulatorConfig, state: BeSimulatorState): BeExpressionExecutor =
    throw UnsupportedOperationException(s"Object execution is not supported by this simulator: ${getClass.getSimpleName}")
}

case class BeCurrentInstance(className: BeEntityName) extends BeObjectExpression derives ReadWriter {
  protected def children = Seq.empty
  protected def replace(children: Map[BeChildRole, BeExpression]) = this
  override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {
    override def staticType: BeDataType = BeDataType.ClassType(className)
    override def hasSideEffects: Boolean = false
  }
}

case class BeMemberAccess(receiver: BeExpression, member: BeDefineVariable) extends BeObjectExpression derives ReadWriter {
  protected def children = Seq(BeChildRole.Receiver -> receiver)
  protected def replace(children: Map[BeChildRole, BeExpression]) = copy(receiver = children.getOrElse(BeChildRole.Receiver, receiver))
  override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {
    override def staticType: BeDataType = member.variableType
    override def hasSideEffects: Boolean = receiver.staticInformationSubtree.hasSideEffects
  }
}

case class BeAssignMember(receiver: BeExpression, member: BeDefineVariable, value: BeExpression) extends BeObjectExpression derives ReadWriter {
  protected def children = Seq(BeChildRole.Receiver -> receiver, BeChildRole.ValueInAssignment -> value)
  protected def replace(children: Map[BeChildRole, BeExpression]) = copy(
    receiver = children.getOrElse(BeChildRole.Receiver, receiver), value = children.getOrElse(BeChildRole.ValueInAssignment, value))
  override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {
    override def syntaxErrors: Seq[BeInfo] = BeInfo.typeMismatchInfo("member assignment", member.variableType, value.staticInformationExpression.staticType).toList
    override def hasSideEffects: Boolean = true
  }
}
