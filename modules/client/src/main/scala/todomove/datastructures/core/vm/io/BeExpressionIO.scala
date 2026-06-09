package todomove.datastructures.core.vm.io

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import todomove.datastructures.core.vm.controlflow.ControlFlowType
import todomove.datastructures.core.vm.types.{BeChildRole, BeScope}

trait BeExpressionIO {

  def toStringInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, skipUnparsable: Boolean = false): String = ""

  def toBlock(): BeBlock = ???

  def toExpressionLines(myScope: BeScope, myStack: List[ControlFlowType]): BeCodeLines = ???


}
