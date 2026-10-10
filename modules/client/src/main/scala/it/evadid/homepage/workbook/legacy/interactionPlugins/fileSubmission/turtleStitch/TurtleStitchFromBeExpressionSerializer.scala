package it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.workbook.elements.interactionElements.programming.state.snap.{SnapCanvasLayout, SnapProjectXml}

object TurtleStitchFromBeExpressionSerializer {

  /** @param previousXml XML being replaced; its custom block definitions are merged forward */
  def toXml(
      expression: BeExpression,
      projectName: String = "fromBeExpression",
      canvasLayout: SnapCanvasLayout = SnapCanvasLayout.empty,
      previousXml: String = ""
  ): String =
    SnapProjectXml.toXml(expression, projectName, canvasLayout, previousXml)
}
