package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.vm.BeProgram
import upickle.default.*

/** Which editor last produced the stored program. */
enum ProgrammingEditorKind derives ReadWriter:
  case Snap, Python

/**
 * Interaction state for ProgrammingExercise.
 *
 * Each editor stores its own format. Snap XML and Python are converted only when
 * the student switches editors. BeProgram is a derived view, never stored.
 */
sealed trait ProgrammingExerciseState:
  def editor: ProgrammingEditorKind

  /** Identity used to ignore echoes of our own writes. Python ignores snapBase. */
  def fingerprint: String

object ProgrammingExerciseState {
  /** Snap project XML, stored as Snap produced it. */
  final case class SnapXml(xml: String) extends ProgrammingExerciseState:
    override def editor: ProgrammingEditorKind = ProgrammingEditorKind.Snap
    override def fingerprint: String = s"snap:$xml"

    /** Stored project XML. Prefer [[xml]] at new call sites. */
    def snapXml: String = xml

  /**
   * Python source, stored verbatim (comments and formatting included).
   *
   * @param snapBase Snap XML from before the last switch to Python. Supplies
   *                 custom block definitions and, when script markers are gone,
   *                 the previous canvas layout on the way back.
   */
  final case class PythonSource(source: String, snapBase: Option[String] = None) extends ProgrammingExerciseState:
    override def editor: ProgrammingEditorKind = ProgrammingEditorKind.Python
    override def fingerprint: String = s"python:$source"

  def apply(xml: String): SnapXml = SnapXml(xml)

  /** @param previousXml XML being replaced; custom block definitions are merged forward */
  def fromProgram(
      program: BeProgram,
      canvasLayout: SnapCanvasLayout = SnapCanvasLayout.empty,
      previousXml: String = ""
  ): SnapXml =
    SnapXml(SnapCustomBlockMerge.applyProgram(program, canvasLayout, previousXml))

  def mini: SnapXml =
    SnapXml(SnapProjectXml.mini)

  def empty: SnapXml =
    SnapXml(SnapProjectXml.empty)

  /** Starter program when an exercise opens in the Python editor. */
  def miniPython: PythonSource =
    PythonSource(
      """receive_go()
        |forward(100)
        |forward(100)
        |""".stripMargin
    )

  def fingerprint(state: ProgrammingExerciseState): String =
    state.fingerprint
}
