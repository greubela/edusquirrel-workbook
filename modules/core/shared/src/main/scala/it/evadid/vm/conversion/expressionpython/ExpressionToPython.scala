package it.evadid.vm.conversion.expressionpython

import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.vm.code.abstractions.BeExpression

import it.evadid.vm.conversion.snapexpression.SnapExpressionSupport.*

object ExpressionToPython {
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
