package it.evadid.vm.conversion.expressionjava

import it.evadid.core.datastructures.language.AppLanguage.{English, Java}
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateJavaString

object ExpressionToJava {
  def convert(expression: BeExpression): ProgrammingStateJavaString =
    ProgrammingStateJavaString(it.evadid.vm.conversion.javaexpression.JavaToBeExpressionParser.withoutEntityHints(
      expression.structureInfo.toStringInLanguage(Java, English, false)))
}
