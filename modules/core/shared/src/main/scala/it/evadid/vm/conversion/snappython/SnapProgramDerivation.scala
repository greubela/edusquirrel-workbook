package it.evadid.vm.conversion.snappython

import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.conversion.expressionpython.ExpressionToPython
import it.evadid.vm.conversion.snapexpression.SnapExpressionSupport
import it.evadid.vm.conversion.turtlestitchexpression.TurtleStitchToBeExpressionParser
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw
import it.evadid.workbook.elements.interactionElements.programming.state.snap.{SnapCanvasLayout, SnapCanvasScript}

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
  ) derives upickle.default.ReadWriter {
    def applyBlockedMessage: Option[String] =
      if pythonCompatible then None
      else
        val listed = unsupportedSelectors.take(5).mkString(", ")
        Some(
          s"These blocks cannot convert to Python: $listed. Apply is disabled so they are not lost."
        )
  }

  def fromState(state: ProgrammingStateRaw): DerivedView =
    fromXml(state.code)

  def fromXml(xml: String): DerivedView = {
    val source = SourceParsing.parse(xml, SourceLanguage.Snap)
    val unsupported = source.unsupportedSelectors
    val parsed = if unsupported.isEmpty then source.parsed else
      TurtleStitchToBeExpressionParser.ParseWithLayout(
        it.evadid.vm.code.errors.BeExpressionUnsupported(xml, parsedFromLanguage = Some(it.evadid.core.datastructures.language.AppLanguage.SnapLanguage)),
        SnapCanvasLayout.empty)
    val program = BeProgram(parsed.expression)
    val astOk = SnapExpressionSupport.validateSubset(program.fullProgram).isRight
    val pythonCompatible = unsupported.isEmpty && astOk
    val python = if pythonCompatible then ExpressionToPython.printedPython(parsed.expression) else ""
    DerivedView(program, parsed.canvasLayout, python, pythonCompatible, unsupported)
  }
  /** One Snap top-level script rendered as Python (plus canvas position). */
  final case class ScriptView(index: Int, x: Int, y: Int, python: String) derives upickle.default.ReadWriter

  /** Partition top-level BeProgram statements by derived canvas layout. */
  def scriptsOf(state: ProgrammingStateRaw): List[ScriptView] = {
    val derived = SnapProgramDerivation.fromState(state)
    if !derived.pythonCompatible then return Nil
    val statements =
      SnapExpressionSupport.scriptStatements(
        SnapExpressionSupport.topLevelStatements(derived.program.fullProgram)
      )
    if statements.isEmpty then Nil
    else
      val chunks =
        if derived.canvasLayout.isEmpty ||
            !SnapExpressionSupport.layoutMatches(derived.canvasLayout, statements.size) then
          List((156, 66, statements))
        else
          splitByLayout(statements, derived.canvasLayout.scripts.toList)
      chunks.zipWithIndex.map { case ((x, y, chunk), idx) =>
        ScriptView(idx + 1, x, y, renderStatements(chunk))
      }
  }

  private def splitByLayout(
      statements: List[BeExpression],
      scripts: List[SnapCanvasScript]
  ): List[(Int, Int, List[BeExpression])] = {
    scripts.foldLeft((statements, List.empty[(Int, Int, List[BeExpression])])) {
      case ((remaining, chunks), script) =>
        val (chunk, rest) = remaining.splitAt(script.callCount)
        (rest, (script.x, script.y, chunk) :: chunks)
    }._2.reverse
  }

  private def renderStatements(statements: List[BeExpression]): String =
    if statements.isEmpty then ""
    else
      BeStartProgram(BeSequence.optionalBody(statements))
        .structureInfo
        .toStringInLanguage(Python, English, false)
        .trim
}
