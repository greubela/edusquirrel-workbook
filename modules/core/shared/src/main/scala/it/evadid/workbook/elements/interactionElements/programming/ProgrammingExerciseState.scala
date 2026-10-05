package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression

/** The source representation currently edited by a programming exercise. */
sealed trait ProgrammingState {
  /** Compatibility accessor for consumers that only accept Snap states. */
  def snapXml: String =
    throw new IllegalStateException(s"${getClass.getSimpleName} does not contain Snap XML")
}

final case class ProgrammingStateBeExpression(expression: BeExpression) extends ProgrammingState
final case class ProgrammingStateSnapXml(override val snapXml: String) extends ProgrammingState
final case class ProgrammingStateSnapXMLWithAdditionalFloatingObjects(
    override val snapXml: String,
    additionalFloatingObjects: List[String]
) extends ProgrammingState
final case class ProgrammingStatePythonString(code: String) extends ProgrammingState
final case class ProgrammingStateJavaString(code: String) extends ProgrammingState

/**
 * Source-compatible name for the former, Snap-only exercise state. New code
 * should accept [[ProgrammingState]] and narrow only at an editor boundary.
 */
type ProgrammingExerciseState = ProgrammingStateSnapXml

object ProgrammingExerciseState {
  def apply(snapXml: String): ProgrammingStateSnapXml = ProgrammingStateSnapXml(snapXml)
  def unapply(state: ProgrammingStateSnapXml): Some[String] = Some(state.snapXml)

  /** @param previousXml XML being replaced; custom block definitions are merged forward */
  def fromProgram(
      program: BeProgram,
      canvasLayout: SnapCanvasLayout = SnapCanvasLayout.empty,
      previousXml: String = ""
  ): ProgrammingStateSnapXml =
    ProgrammingStateSnapXml(SnapCustomBlockMerge.applyProgram(program, canvasLayout, previousXml))

  def mini: ProgrammingStateSnapXml = ProgrammingStateSnapXml(SnapProjectXml.mini)
  def empty: ProgrammingStateSnapXml = ProgrammingStateSnapXml(SnapProjectXml.empty)
  def fingerprint(state: ProgrammingStateSnapXml): String = state.snapXml
}

object ProgrammingState {
  def fingerprint(state: ProgrammingState): String = state match
    case ProgrammingStateBeExpression(expression) => s"expression:${expression.toString}"
    case ProgrammingStateSnapXml(xml) => s"snap:$xml"
    case ProgrammingStateSnapXMLWithAdditionalFloatingObjects(xml, objects) =>
      s"snap-floating:$xml\u0000${objects.mkString("\u0000")}"
    case ProgrammingStatePythonString(code) => s"python:$code"
    case ProgrammingStateJavaString(code) => s"java:$code"
}
