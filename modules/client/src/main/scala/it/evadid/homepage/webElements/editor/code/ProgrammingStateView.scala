package it.evadid.homepage.webElements.editor.code

import com.raquo.laminar.api.L.*
import it.evadid.workbook.interactions.programming.state.ProgrammingState

object ProgrammingStateView {
  /** The answer in one editor's representation.
    *
    * After a language switch the converted answer reaches the outgoing editor before Laminar unmounts it. The
    * view then reads `inactive` and drops the editor's writes, so an outgoing editor can never overwrite the
    * converted answer with its placeholder.
    */
  def apply[R](state: Var[ProgrammingState], inactive: R)(read: PartialFunction[ProgrammingState, R])(
      write: R => ProgrammingState): Var[R] =
    state.zoomLazy(read.applyOrElse(_, (_: ProgrammingState) => inactive))((current, value) =>
      if read.isDefinedAt(current) then write(value) else current)
}
