package it.evadid.vm.io

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.vm.controlflow.ControlFlowType
import it.evadid.vm.naming.{CodeRepresentationConfig, NamingStyle}
import it.evadid.vm.types.BeScope

/** Pure IO-facing expression representation. Rendering is intentionally handled by client-side factories. */
trait BeExpressionIO {

  def toStringWithConfig(config: CodeRepresentationConfig): String = ""

  def toStringInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, skipUnparsable: Boolean = false): String =
    toStringWithConfig(CodeRepresentationConfig(programmingLanguage, humanLanguage, NamingStyle.SnakeCase, skipUnparsable))

  // def toExpressionLines(myScope: BeScope, myStack: List[ControlFlowType]): BeCodeLines = ???

  // todo 
  def toExpressionLines(myScope: BeScope, priorLines: BeExpressionCodeLine): List[BeExpressionCodeLine] = ???

}
