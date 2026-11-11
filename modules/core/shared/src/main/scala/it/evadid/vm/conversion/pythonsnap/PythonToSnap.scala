package it.evadid.vm.conversion.pythonsnap

import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapCanvasLayout

import it.evadid.vm.conversion.expressionsnap.ExpressionToSnap
import it.evadid.vm.conversion.pythonexpression.PythonToExpression

import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateSnapXml

import it.evadid.vm.conversion.snapexpression.SnapExpressionSupport.*

object PythonToSnap {
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
  ): Either[String, ProgrammingStateSnapXml] =
    try
      val program = PythonToExpression.program(source)
      validateSubset(program.fullProgram) match
        case Left(message) => Left(message)
        case Right(statements) =>
          if statements.isEmpty && collectUserFunctionArities(program.fullProgram).isEmpty then
            Right(ProgrammingStateSnapXml.empty)
          else
            val layout = reconcileLayout(previousLayout, scriptStatementCount(statements))
            Right(ExpressionToSnap.fromProgram(program, layout, previousXml))
    catch
      case e: Throwable =>
        val detail = Option(e.getMessage).filter(_.nonEmpty).getOrElse(e.getClass.getSimpleName)
        Left(s"Parse error; keeping existing blocks. ($detail)")

}
