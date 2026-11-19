package it.evadid.vm.conversion.pythonsnap

import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateSnapScalaModel
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapCanvasLayout

import it.evadid.vm.conversion.expressionsnap.ExpressionToSnap
import it.evadid.vm.conversion.pythonexpression.PythonToExpression

import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw

import it.evadid.vm.conversion.snapexpression.SnapExpressionSupport.*

object PythonToSnap {
  /** Apply Python while retaining the current project's layout and authored block definitions. */
  def replacing(current: ProgrammingStateRaw, source: String): Either[String, ProgrammingStateRaw] = {
    val derived = it.evadid.vm.conversion.snappython.SnapProgramDerivation.fromState(current)
    applyPython(source, derived.canvasLayout, current.code)
      .map(next => current.copy(code = next.code))
  }

  /**
   * Parse Python, validate the turtle subset, and write Snap XML.
   * @param previousLayout derived script partitions from the current XML, if known
   * @param previousXml the XML being replaced; its custom block definitions are merged
   *                    forward so labels, slot types and categories survive
   */
  def applyPython(
      source: String,
      previousLayout: SnapCanvasLayout = SnapCanvasLayout.empty,
      previousXml: String = ""
  ): Either[String, ProgrammingStateRaw] =
    try
      val program = PythonToExpression.program(source)
      validateSubset(program.fullProgram) match
        case Left(message) => Left(message)
        case Right(statements) =>
          if statements.isEmpty && collectUserFunctionArities(program.fullProgram).isEmpty then
            Right(ProgrammingStateSnapScalaModel.empty.toSnapXml)
          else
            val layout = reconcileLayout(previousLayout, scriptStatementCount(statements))
            Right(ExpressionToSnap.fromProgram(program, layout, previousXml))
    catch
      case e: Throwable =>
        val detail = Option(e.getMessage).filter(_.nonEmpty).getOrElse(e.getClass.getSimpleName)
        Left(s"Parse error; keeping existing blocks. ($detail)")

}
