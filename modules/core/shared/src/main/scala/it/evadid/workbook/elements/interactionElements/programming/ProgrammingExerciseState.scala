package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.vm.BeProgram

/**
 * Interaction state for ProgrammingExercise.
 *
 * Canonical field is Snap project XML. BeProgram, Python, and canvas layout are
 * derived views produced at consumer boundaries.
 */
final case class ProgrammingExerciseState(snapXml: String)

object ProgrammingExerciseState {
  def fromProgram(
      program: BeProgram,
      canvasLayout: SnapCanvasLayout = SnapCanvasLayout.empty
  ): ProgrammingExerciseState =
    ProgrammingExerciseState(SnapProjectXml.toXml(program.fullProgram, canvasLayout = canvasLayout))

  def mini: ProgrammingExerciseState =
    ProgrammingExerciseState(SnapProjectXml.mini)

  def empty: ProgrammingExerciseState =
    ProgrammingExerciseState(SnapProjectXml.empty)

  /** Exact stored XML; Snap may normalize attribute order after the first open. */
  def fingerprint(state: ProgrammingExerciseState): String =
    state.snapXml
}
