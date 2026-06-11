package todomove.datastructures.core.vm.io

import it.evadid.core.datastructures.language.AppLanguage.*
import todomove.datastructures.core.vm.controlflow.ControlFlowType
import todomove.datastructures.core.vm.types.{BeChildRole, BeScope}

/** Pure IO-facing expression representation. Rendering is intentionally handled by client-side factories. */
trait BeExpressionIO {

  def toStringInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, skipUnparsable: Boolean = false): String = ""

  def toExpressionLines(myScope: BeScope, myStack: List[ControlFlowType]): BeCodeLines = ???


}
