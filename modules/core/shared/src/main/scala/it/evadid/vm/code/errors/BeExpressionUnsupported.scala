package it.evadid.vm.code.errors

import it.evadid.core.datastructures.language.*
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.tree.BeExpressionNode
import it.evadid.vm.controlflow.ControlFlowType.ControlFlowDown
import it.evadid.vm.io.{BeExpressionStructureInfo, BeSegmentedCodeElement}
import it.evadid.vm.naming.CodeRepresentationConfig
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.*

case class BeExpressionUnsupported(originalSource: String) extends BeExpression {

  override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {

    override def staticType: BeDataType = BeDataType.Error

    override def syntaxErrors: Seq[BeInfo] =
      List(BeInfo(LanguageMap.universalMap(s"Unknown Python structure: $originalSource"), BeInfo.SyntaxError.UnsupportedBlock))

  }

  override lazy val structureInfo: BeExpressionStructureInfo[?] = new BeExpressionStructureInfo[BeExpressionUnsupported](this) {
    override def withReplacedChildren(newChildren: Map[BeChildRole, BeExpression]): BeExpressionUnsupported = BeExpressionUnsupported.this
    override def toJavaStyleLines(myInfo: BeChildInfo): Seq[BeSegmentedCodeElement] = asExpressionLine(ControlFlowDown, myInfo)
    override def getChildrenAndExtension(myScope: BeScope): Seq[BeExpressionNode] = Seq.empty
  }


}
