package it.evadid.vm.conversion.expressionsnap

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}

import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.conversion.ConversionResult.converted
import it.evadid.vm.conversion.snapexpression.SnapExpressionSupport
import it.evadid.workbook.interactions.programming.state.ProgrammingStateRaw
import it.evadid.workbook.interactions.programming.state.snap.SnapCanvasLayout

object ExpressionToSnap {
  def convert(expression: BeExpression): ProgrammingStateRaw = {
    converted(SnapExpressionSupport.validateSubset(expression))
    fromProgram(BeProgram(expression))
  }

  def fromProgram(program: BeProgram, canvasLayout: SnapCanvasLayout = SnapCanvasLayout.empty,
      previousXml: String = ""): ProgrammingStateRaw =
    ProgrammingStateRaw(SnapLanguage, SnapCustomBlockMerge.applyProgram(program, canvasLayout, previousXml))
}
