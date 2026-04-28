package interactionPlugins.blockEnvironment.programming.blockdisplay.data

import com.raquo.laminar.api.L
import contentmanagement.webElements.svg.shapes.composite.ShapeAroundShape
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.*
import contentmanagement.webElements.svg.shapes.{BeShape, ControlFlowShape, TextShape}
import datastructures.core.vm.code.defining.BeDefineVariable
import datastructures.core.vm.code.tree.BeExpressionNode
import interactionPlugins.blockEnvironment.programming.blockdisplay.*

case class BeBlockDefineVariable(
                                  varDef: BeDefineVariable,
                                ) extends BeBlockSingleShape {

  override def renderShape(childrenShapes: List[(BeExpressionNode, BeShape)], renderingInformation: RenderingInformation): (ControlFlowShape, BeShape) = {
    val outerShape = varDef.variableType.createContainerShape.get
    val textShape = TextShape(varDef.name, renderingInformation.factory.invertedTextAmends)
    val res = ShapeAroundShape(outerShape, textShape)
      .addAmends(renderingInformation.factory.variableColorsDefAmend)
    (ControlFlowEmpty(), res)
  }


}
