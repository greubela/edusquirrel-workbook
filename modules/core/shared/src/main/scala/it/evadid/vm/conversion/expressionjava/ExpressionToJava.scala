package it.evadid.vm.conversion.expressionjava

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.core.datastructures.language.AppLanguage.{English, Java}
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw

object ExpressionToJava {
  def convert(expression: BeExpression): ProgrammingStateRaw =
    ProgrammingStateRaw(Java, expression.structureInfo.toStringInLanguage(Java, English, false))
}
