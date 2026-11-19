package it.evadid.vm.conversion.expressionpython

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.vm.conversion.ConversionResult.converted
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw

import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.vm.code.abstractions.BeExpression

import it.evadid.vm.conversion.snapexpression.SnapExpressionSupport.*

object ExpressionToPython {
  /** Source for editing: the main sequence becomes a called `def main()`, see [[PythonEntryPoint]]. */
  def convert(expression: BeExpression): ProgrammingStateRaw = ProgrammingStateRaw(Python, expression match {
    case program: it.evadid.vm.code.others.BeStartProgram => printedPython(PythonEntryPoint.wrap(program))
    case other => printedPython(other)
  })

  /** Preserve the VM's variable, parameter and return types when switching to Python. */
  def printedPython(expression: BeExpression): String = {
    val rendered = expression.structureInfo.toStringInLanguage(Python, English, false)
    val variableAnnotation = """^\s*[A-Za-z_][A-Za-z0-9_]*\s*:\s*.*$""".r
    val annotationSource = rendered.linesIterator
      .filter(line => line.trim.startsWith("def ") || variableAnnotation.matches(line))
      .map(_.takeWhile(_ != '='))
      .mkString("\n")
    val usesTurtle = expression.recToTree(false, it.evadid.vm.types.BeChildInfo(
      it.evadid.vm.types.BeChildRole.NoRole, it.evadid.vm.types.BeScope.GlobalScope())).values.exists {
      case it.evadid.vm.code.tree.BeExpressionReference(_, call: it.evadid.vm.code.usage.BeFunctionCall) =>
        it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand.isTurtleMethod(call.funcDef)
      case _ => false
    }
    val imports = Option.when(usesTurtle)("import turtle").toList ++ List(
      "Any" -> "from typing import Any",
      "date" -> "from datetime import date"
    ).collect {
      case (typeName, importLine) if
          s"(?:[:|]|->)\\s*$typeName\\b".r.findFirstIn(annotationSource).nonEmpty => importLine
    }
    if imports.isEmpty then rendered else imports.mkString("", "\n", "\n\n") + rendered
  }

}
