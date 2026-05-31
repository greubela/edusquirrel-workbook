package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.data

import com.raquo.laminar.api.L
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlockSingleShape, RenderingInformation}
import todomove.datastructures.core.vm.code.defining.BeDefineVariable
import todomove.datastructures.core.vm.code.tree.BeExpressionNode
import todomove.webElementsOld.webElements.svg.shapes.{BeShape, ControlFlowShape, TextShape}
import todomove.webElementsOld.webElements.svg.shapes.composite.ShapeAroundShape
import todomove.webElementsOld.webElements.svg.shapes.controlflow.singleWidth.ControlFlowEmpty

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
