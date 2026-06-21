package it.evadid.workbook.vm.io

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.workbook.vm.controlflow.ControlFlowType
import it.evadid.workbook.vm.types.{BeChildRole, BeScope}

/** Pure IO-facing expression representation. Rendering is intentionally handled by client-side factories. */
trait BeExpressionIO {

  def toStringInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, skipUnparsable: Boolean = false): String = ""

  def toExpressionLines(myScope: BeScope, myStack: List[ControlFlowType]): BeCodeLines = ???


}
