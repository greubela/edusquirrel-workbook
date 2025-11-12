package interactionPlugins.blockEnvironment.programming.blockdisplay.other

import contentmanagement.model.language.LanguageMap
import contentmanagement.model.vm.code.errors.BeExpressionUnsupported
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.webElements.svg.shapes.{BeShape, ControlFlowShape, TextShape}
import contentmanagement.webElements.svg.shapes.composite.ShapeAroundShape
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.*
import contentmanagement.webElements.svg.shapes.datatypes.RectangleShape
import interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlock, BeBlockSingleShape, RenderingInformation}
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer

case class BeBlockUnsupported(beExpressionUnsupported: BeExpressionUnsupported) extends BeBlockSingleShape {

  override def renderShape(childrenShapes: List[(BeExpressionNode, BeShape)], renderingInformation: RenderingInformation): (ControlFlowShape, BeShape) = {

    val container = RectangleShape
    val text = TextShape(LanguageMap.universalMap(beExpressionUnsupported.originalSource)).addAmends(renderingInformation.factory.defaultTextAmends)
    val res = ShapeAroundShape(container, text) addAmends (renderingInformation.factory.errorColorsAmend)

    (ControlFlowDirected(true, false), res)
  }

}
