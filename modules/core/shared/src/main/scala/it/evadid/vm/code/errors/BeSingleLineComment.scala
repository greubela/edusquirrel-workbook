package it.evadid.vm.code.errors

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.tree.BeExpressionNode
import it.evadid.vm.controlflow.ControlFlowType.ControlFlowDown
import it.evadid.vm.io.{BeExpressionStructureInfo, BeSegmentedCodeElement}
import it.evadid.vm.naming.CodeRepresentationConfig
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.*

case class BeSingleLineComment(commentStr: LanguageMap[HumanLanguage]) extends BeExpression {

  override lazy val structureInfo: BeExpressionStructureInfo[?] = new BeExpressionStructureInfo[BeSingleLineComment](this) {
    override def withReplacedChildren(newChildren: Map[BeChildRole, BeExpression]): BeSingleLineComment = BeSingleLineComment.this
    override def toJavaStyleLines(myInfo: BeChildInfo): Seq[BeSegmentedCodeElement] = asExpressionLine(ControlFlowDown, myInfo)
    override def getChildrenAndExtension(myScope: BeScope): Seq[BeExpressionNode] = Seq.empty
  }
}
