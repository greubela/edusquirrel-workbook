package it.evadid.vm.io

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.vm.controlflow.ControlFlowType
import it.evadid.vm.io.BeSegmentedCodeElement.BeExpressionLine
import it.evadid.vm.io.stringPrinter.java.BeExpressionToJavaStr
import it.evadid.vm.io.stringPrinter.python.BeExpressionToPythonString
import it.evadid.vm.types.{BeChildInfo, BeChildRole, BeScope}


object BeExpressionStructureInfo {



}

/** Pure IO-facing expression representation. Rendering is intentionally handled by client-side factories. */
trait BeExpressionStructureInfo[T <: BeExpression](expression: T) {

  final def toStringInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, skipUnparsable: Boolean = false): String = {
    programmingLanguage.match {
      case Python => BeExpressionToPythonString(humanLanguage, skipUnparsable).forExpression(expression)
      case Java => BeExpressionToJavaStr(humanLanguage, skipUnparsable).forExpression(expression)
      case _ => throw new IllegalArgumentException(s"Language ${programmingLanguage} not implemented yet for toStringInLanguage!")
    }
  }

  protected final def asExpressionLine(cf: ControlFlowType, myInfo: BeChildInfo): Seq[BeSegmentedCodeElement] = {
    List(BeExpressionLine(cf, BeExpressionReference(myInfo, expression)))
  }

  def withReplacedChildren(newChildren: Map[BeChildRole, BeExpression]): T

  def toJavaStyleLines(myInfo: BeChildInfo): Seq[BeSegmentedCodeElement]

  final def getChildrenAsReference(myScope: BeScope): Seq[BeExpressionReference] = {
    getChildrenAndExtension(myScope).flatMap {
      case n@BeExpressionReference(childInfo, expr) => Some(n)
      case _ => None
    }
  }

  def getChildrenAndExtension(myScope: BeScope): Seq[BeExpressionNode]

  final def getChildren(withExtensions: Boolean, myScope: BeScope): Seq[BeExpressionNode] = {
    if (withExtensions) getChildrenAndExtension(myScope)
    else getChildrenAsReference(myScope)
  }

}
