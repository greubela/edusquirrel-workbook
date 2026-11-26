package it.evadid.vm.conversion.expressionpython

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.vm.conversion.ConversionResult.converted
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw

import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.vm.code.abstractions.BeExpression

import it.evadid.vm.conversion.snapexpression.SnapExpressionSupport.*

import it.evadid.vm.conversion.dependencies.KnownDependencies

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
    // Single-line dependency definitions are imports and lead the module; helpers follow them.
    val (dependencyImports, helpers) = KnownDependencies.definitionsFor(expression, Python).partition(!_.contains('\n'))
    if helpers.contains(KnownDependencies.ieeeDivide.definition(Python).get) && expression.subtree.exists {
      case f: it.evadid.vm.code.defining.BeDefineFunction => pythonNameOf(f) == "ieee_divide"
      case v: it.evadid.vm.code.defining.BeDefineVariable => v.name == KnownDependencies.ieeeDivide.structureName
      case _ => false
    } then throw it.evadid.vm.code.errors.ProgrammingConversionException.unsupported("The name ieee_divide conflicts with the floating-point division helper.")
    val imports = dependencyImports ++ List(
      "Any" -> "from typing import Any",
      "date" -> "from datetime import date"
    ).collect {
      case (typeName, importLine) if
          s"(?:[:|]|->)\\s*$typeName\\b".r.findFirstIn(annotationSource).nonEmpty => importLine
    }
    Seq(imports.mkString("\n"), helpers.mkString("\n\n")).filter(_.nonEmpty).map(_ + "\n\n").mkString + rendered
  }

}
