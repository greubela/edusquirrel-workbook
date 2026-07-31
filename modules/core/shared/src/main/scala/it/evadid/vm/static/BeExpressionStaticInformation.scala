package it.evadid.vm.static

import it.evadid.vm.code.abstractions.{BeDefineStructure, BeExpression}
import it.evadid.vm.code.tree.BeExpressionNode
import it.evadid.vm.controlflow.ControlFlowType.ControlFlowDown
import it.evadid.vm.io.{BeExpressionStructureInfo, BeSegmentedCodeElement}
import it.evadid.vm.types.*

trait BeExpressionStaticInformation {

  def staticType: BeDataType = BeDataType.Unit

  def staticValue: Option[BeDataValue] = None

  def syntaxErrors: Seq[BeInfo] = List()

  def hasSideEffects: Boolean = false

  def getDefinitions: BeDefineStructure = new BeDefineStructure() {
    private val definition: BeDefineStructure = this
    override lazy val structureInfo: BeExpressionStructureInfo[?] = new BeExpressionStructureInfo[BeDefineStructure](this) {
      override def withReplacedChildren(newChildren: Map[BeChildRole, BeExpression]): BeDefineStructure = definition
      override def toJavaStyleLines(myInfo: BeChildInfo): Seq[BeSegmentedCodeElement] = asExpressionLine(ControlFlowDown, myInfo)
      override def getChildrenAndExtension(myScope: BeScope): Seq[BeExpressionNode] = Seq.empty
    }
  }
}

object BeExpressionStaticInformation {

  val empty: BeExpressionStaticInformation = new BeExpressionStaticInformation() {

  }

}
