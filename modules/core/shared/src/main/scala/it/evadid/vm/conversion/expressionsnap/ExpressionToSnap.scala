package it.evadid.vm.conversion.expressionsnap

import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.conversion.ConversionResult.converted
import it.evadid.vm.conversion.snapexpression.SnapExpressionSupport
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateSnapXml
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapCanvasLayout

object ExpressionToSnap {
  def convert(expression: BeExpression): ProgrammingStateSnapXml =
    converted(SnapExpressionSupport.validateSubset(expression).map(_ => fromProgram(BeProgram(expression))))

  def fromProgram(program: BeProgram, canvasLayout: SnapCanvasLayout = SnapCanvasLayout.empty,
      previousXml: String = ""): ProgrammingStateSnapXml =
    ProgrammingStateSnapXml(SnapCustomBlockMerge.applyProgram(program, canvasLayout, previousXml))
}
