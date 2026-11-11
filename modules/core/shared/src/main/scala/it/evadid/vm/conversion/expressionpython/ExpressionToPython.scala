package it.evadid.vm.conversion.expressionpython

import it.evadid.vm.conversion.ConversionResult.converted
import it.evadid.vm.conversion.javaexpression.checked.JavaTurtleVmPrograms
import it.evadid.vm.conversion.javapython.JavaToPython
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStatePythonString

import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.vm.code.abstractions.BeExpression

import it.evadid.vm.conversion.snapexpression.SnapExpressionSupport.*

object ExpressionToPython {
  def convert(expression: BeExpression): ProgrammingStatePythonString = expression match {
    case root: JavaTurtleVmPrograms.Root => converted(JavaToPython.toPython(JavaTurtleVmPrograms.fromRoot(root)))
    case _ => ProgrammingStatePythonString(printedPython(expression))
  }

  /** Preserve the VM's variable, parameter and return types when switching to Python. */
  def printedPython(expression: BeExpression): String = {
    val rendered = expression.structureInfo.toStringInLanguage(Python, English, false)
    val variableAnnotation = """^\s*[A-Za-z_][A-Za-z0-9_]*\s*:\s*.*$""".r
    val annotationSource = rendered.linesIterator
      .filter(line => line.trim.startsWith("def ") || variableAnnotation.matches(line))
      .map(_.takeWhile(_ != '='))
      .mkString("\n")
    val imports = List(
      "Any" -> "from typing import Any",
      "date" -> "from datetime import date"
    ).collect {
      case (typeName, importLine) if
          s"(?:[:|]|->)\\s*$typeName\\b".r.findFirstIn(annotationSource).nonEmpty => importLine
    }
    if imports.isEmpty then rendered else imports.mkString("", "\n", "\n\n") + rendered
  }

}
