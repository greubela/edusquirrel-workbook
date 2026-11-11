package it.evadid.vm.conversion.expressionjava

import it.evadid.core.datastructures.language.AppLanguage.{English, Java}
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.conversion.javaexpression.checked.JavaTurtleVmPrograms
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateJavaString

object ExpressionToJava {
  def convert(expression: BeExpression): ProgrammingStateJavaString = expression match {
    case root: JavaTurtleVmPrograms.Root => ProgrammingStateJavaString(root.bindings.source.source)
    case _ => ProgrammingStateJavaString(expression.structureInfo.toStringInLanguage(Java, English, false))
  }
}
