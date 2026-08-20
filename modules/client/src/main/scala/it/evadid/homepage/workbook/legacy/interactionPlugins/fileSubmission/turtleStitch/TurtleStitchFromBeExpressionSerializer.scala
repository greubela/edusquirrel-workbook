package it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.workbook.elements.interactionElements.programming.{SnapCanvasLayout, SnapProjectXml}

object TurtleStitchFromBeExpressionSerializer {

  def toXml(
      expression: BeExpression,
      projectName: String = "fromBeExpression",
      canvasLayout: SnapCanvasLayout = SnapCanvasLayout.empty
  ): String =
    SnapProjectXml.toXml(expression, projectName, canvasLayout)
}
