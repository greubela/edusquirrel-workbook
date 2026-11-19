package it.evadid.vm.conversion.pythonexpression

import it.evadid.vm.BeProgram
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.conversion.pythonexpression.PythonParser

object PythonToExpression {
  /** A `def main(): …` entry point called at the end is the main sequence, see [[it.evadid.vm.conversion.expressionpython.PythonEntryPoint]]. */
  def program(source: String): BeProgram =
    BeProgram(it.evadid.vm.conversion.expressionpython.PythonEntryPoint.inline(
      BeStartProgram(PythonParser.default.parsePythonWithDetails(source).codeExpression)))
}
