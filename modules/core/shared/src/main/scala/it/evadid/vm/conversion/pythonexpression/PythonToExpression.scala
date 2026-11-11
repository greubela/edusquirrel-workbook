package it.evadid.vm.conversion.pythonexpression

import it.evadid.vm.BeProgram
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.conversion.pythonexpression.PythonParser

object PythonToExpression {
  def program(source: String): BeProgram =
    BeProgram(BeStartProgram(PythonParser.default.parsePythonWithDetails(source).codeExpression))
}
