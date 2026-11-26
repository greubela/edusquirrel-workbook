package it.evadid.vm.parsing.python.clean

import it.evadid.vm.parsing.python.clean.model.PyAST
import it.evadid.vm.parsing.python.clean.model.PyAST.PyProgram
import it.evadid.vm.parsing.source.{SourceIdentity, SourceSpan}

/** Positions belong to occurrences, not structurally equal AST values. */
final class PythonSourceMap private[clean](val source: String, val program: PyProgram,
    private val ranges: Map[SourceIdentity[PyAST], SourceSpan]) {
  def rangeOf(node: PyAST): Option[SourceSpan] = ranges.get(SourceIdentity(node))
}
