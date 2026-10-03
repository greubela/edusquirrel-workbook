package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.parsing.java.JavaToBeExpressionParser

/** The persisted value of a programming exercise.
  *
  * A state retains the representation edited by the user.  Conversion is deliberately
  * explicit and only happens when an editor for another representation is opened.
  */
sealed trait ProgrammingState {
  /** Compatibility view for Snap consumers; conversion is lazy at this boundary. */
  def snapXml: String = ProgrammingState.toSnapXml(this).fold(throw _, _.snapXml)
}

final case class ProgrammingStateBeExpression(expression: BeExpression) extends ProgrammingState
final case class ProgrammingStateSnapXml(override val snapXml: String) extends ProgrammingState
final case class ProgrammingStatePythonString(python: String) extends ProgrammingState
final case class ProgrammingStateJavaString(java: String) extends ProgrammingState

object ProgrammingState {
  def mini: ProgrammingState = ProgrammingStateSnapXml(SnapProjectXml.mini)
  def empty: ProgrammingState = ProgrammingStateSnapXml(SnapProjectXml.empty)

  def fromProgram(program: BeProgram): ProgrammingState =
    ProgrammingStateBeExpression(program.fullProgram)

  def toProgram(state: ProgrammingState): Either[Throwable, BeProgram] = state match
    case ProgrammingStateBeExpression(expression) => Right(BeProgram(expression))
    case ProgrammingStatePythonString(python) => scala.util.Try(BeProgram.fromPythonString(python)).toEither
    case ProgrammingStateJavaString(java) => JavaToBeExpressionParser.parseProgram(java)
    case ProgrammingStateSnapXml(_) =>
      Left(IllegalArgumentException("Snap XML conversion requires the Snap editor parser"))

  /** Convert at the Snap editor boundary, preserving custom blocks when possible. */
  def toSnapXml(state: ProgrammingState): Either[Throwable, ProgrammingStateSnapXml] = state match
    case snap: ProgrammingStateSnapXml => Right(snap)
    case other => toProgram(other).map(program => ProgrammingExerciseState.fromProgram(program))

  def fingerprint(state: ProgrammingState): String = state match
    case ProgrammingStateBeExpression(expression) => "be:" + expression.hashCode()
    case ProgrammingStateSnapXml(xml) => "snap:" + xml
    case ProgrammingStatePythonString(python) => "python:" + python
    case ProgrammingStateJavaString(java) => "java:" + java
}

/** Source-compatible name for the Snap editor's native state. */
type ProgrammingExerciseState = ProgrammingStateSnapXml

object ProgrammingExerciseState {
  def apply(snapXml: String): ProgrammingStateSnapXml = ProgrammingStateSnapXml(snapXml)
  def unapply(state: ProgrammingStateSnapXml): Option[String] = Some(state.snapXml)

  /** @param previousXml XML being replaced; custom block definitions are merged forward */
  def fromProgram(
      program: BeProgram,
      canvasLayout: SnapCanvasLayout = SnapCanvasLayout.empty,
      previousXml: String = ""
  ): ProgrammingStateSnapXml =
    ProgrammingStateSnapXml(SnapCustomBlockMerge.applyProgram(program, canvasLayout, previousXml))

  def mini: ProgrammingStateSnapXml = ProgrammingStateSnapXml(SnapProjectXml.mini)
  def empty: ProgrammingStateSnapXml = ProgrammingStateSnapXml(SnapProjectXml.empty)
  def fingerprint(state: ProgrammingState): String = ProgrammingState.fingerprint(state)
}
