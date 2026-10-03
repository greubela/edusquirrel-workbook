package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch.TurtleStitchToBeExpressionParser
import it.evadid.vm.BeProgram

import scala.util.Try
import it.evadid.workbook.elements.interactionElements.programming.{
  ProgrammingExerciseState,
  SnapCanvasLayout,
  SnapTurtlePythonBridge
}

/**
 * XML → BeProgram / Python derivation for ProgrammingExercise consumers.
 * Canonical state stays Snap XML; this view is never written back except via Python apply.
 */
object SnapProgramDerivation {

  final case class DerivedView(
      program: BeProgram,
      canvasLayout: SnapCanvasLayout,
      python: String,
      pythonCompatible: Boolean,
      unsupportedSelectors: List[String]
  ) {
    def applyBlockedMessage: Option[String] =
      if pythonCompatible then None
      else
        val listed = unsupportedSelectors.take(5).mkString(", ")
        Some(
          s"These blocks cannot convert to Python: $listed. Apply is disabled so they are not lost."
        )
  }

  def fromState(state: ProgrammingExerciseState): DerivedView = state match
    case ProgrammingExerciseState.SnapXml(xml) => fromXml(xml)
    case ProgrammingExerciseState.PythonSource(source, _) =>
      fromPythonSource(source)

  private var derivationCache: Option[(String, DerivedView)] = None

  def fromXml(xml: String): DerivedView =
    derivationCache match
      case Some((cachedXml, cachedView)) if cachedXml == xml => cachedView
      case _ =>
        val view = deriveFromXml(xml)
        derivationCache = Some((xml, view))
        view

  private def fromPythonSource(source: String): DerivedView = {
    val program = Try(BeProgram.fromPythonString(source)).getOrElse(BeProgram.empty)
    DerivedView(program, SnapCanvasLayout.empty, source, pythonCompatible = true, Nil)
  }

  private def deriveFromXml(xml: String): DerivedView = {
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(xml)
    val program = BeProgram(parsed.expression)
    val python = SnapTurtlePythonBridge.printedPython(parsed.expression)
    val unsupported = SnapTurtlePythonBridge.unsupportedSnapSelectors(xml)
    val astOk = SnapTurtlePythonBridge.validateSubset(program.fullProgram).isRight
    val pythonCompatible = unsupported.isEmpty && astOk
    DerivedView(program, parsed.canvasLayout, python, pythonCompatible, unsupported)
  }
}
