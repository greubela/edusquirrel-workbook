package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.vm.BeProgram

/**
 * Interaction state for ProgrammingExercise: program semantics + Snap canvas layout.
 * Layout is a sidecar so BeProgram stays a pure AST (no comment markers).
 */
final case class ProgrammingExerciseState(
    program: BeProgram,
    canvasLayout: SnapCanvasLayout = SnapCanvasLayout.empty
)

object ProgrammingExerciseState {
  def fromProgram(program: BeProgram): ProgrammingExerciseState =
    ProgrammingExerciseState(program, SnapCanvasLayout.empty)

  def mini: ProgrammingExerciseState =
    fromProgram(BeProgram.miniProgram())

  def pythonOf(state: ProgrammingExerciseState): String =
    state.program.fullProgram.expressionIO.toStringInLanguage(Python, English, false)

  /** Stable fingerprint including layout (positions / script splits). */
  def fingerprint(state: ProgrammingExerciseState): String =
    s"${pythonOf(state)}\n${state.canvasLayout.fingerprint}"
}
