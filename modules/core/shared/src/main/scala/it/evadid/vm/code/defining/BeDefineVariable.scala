package it.evadid.vm.code.defining

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.vm.code.abstractions.{BeDefineStructure, BeExpression}
import it.evadid.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.vm.controlflow.ControlFlowType.ControlFlowDown
import it.evadid.vm.io.{BeExpressionStructureInfo, BeSegmentedCodeElement}
import it.evadid.vm.naming.{BeEntityName, CodeRepresentationConfig}
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.*

case class BeDefineVariable(
                             name: BeEntityName,
                             variableType: BeDataType,
                             initValue: Option[BeExpression] = None
                           ) extends BeDefineStructure {




  override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {


    override def hasSideEffects: Boolean = true
  }

  override lazy val structureInfo: BeExpressionStructureInfo[?] = new BeExpressionStructureInfo[BeDefineVariable](this) {
    override def withReplacedChildren(newChildren: Map[BeChildRole, BeExpression]): BeDefineVariable =
      newChildren.get(BeChildRole.ValueForVariable(BeDefineVariable.this)).map(value => copy(initValue = Some(value))).getOrElse(BeDefineVariable.this)

    override def toJavaStyleLines(myInfo: BeChildInfo): Seq[BeSegmentedCodeElement] = asExpressionLine(ControlFlowDown, myInfo)

    override def getChildrenAndExtension(myScope: BeScope): Seq[BeExpressionNode] =
      initValue.map(value => BeExpressionReference(BeChildInfo(BeChildRole.ValueForVariable(BeDefineVariable.this), myScope), value)).toSeq
  }


  override val toString: String = "BeDefineVariable(" + name.toString + ": " + staticInformationExpression.staticType.toString + ")"


}
