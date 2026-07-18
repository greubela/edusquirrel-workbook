package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.data

import com.raquo.laminar.api.L
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlockSingleShape, RenderingInformation}
import it.evadid.vm.code.defining.BeDefineVariable
import it.evadid.vm.code.tree.BeExpressionNode
import it.evadid.vm.naming.NamingStyle
import todomove.webElementsOld.webElements.svg.shapes.{BeShape, ControlFlowShape, TextShape}
import todomove.webElementsOld.webElements.svg.shapes.composite.ShapeAroundShape
import todomove.webElementsOld.webElements.svg.shapes.controlflow.singleWidth.ControlFlowEmpty

case class BeBlockDefineVariable(
                                  varDef: BeDefineVariable,
                                ) extends BeBlockSingleShape {

  override def renderShape(childrenShapes: List[(BeExpressionNode, BeShape)], renderingInformation: RenderingInformation): (ControlFlowShape, BeShape) = {
    val outerShape = BeDataTypeShapeAdapter.containerShapeFor(varDef.variableType).get
    val textShape = TextShape(varDef.name.asLanguageMap(NamingStyle.SnakeCase), renderingInformation.factory.invertedTextAmends)
    val res = ShapeAroundShape(outerShape, textShape)
      .addAmends(renderingInformation.factory.variableColorsDefAmend)
    (ControlFlowEmpty(), res)
  }


}
