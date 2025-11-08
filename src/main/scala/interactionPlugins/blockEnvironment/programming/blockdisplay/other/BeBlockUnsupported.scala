package interactionPlugins.blockEnvironment.programming.blockdisplay.other

import contentmanagement.model.language.LanguageMap
import contentmanagement.model.vm.code.errors.BeExpressionUnsupported
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.webElements.svg.shapes.TextShape
import contentmanagement.webElements.svg.shapes.composite.ShapeAroundShape
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.*
import contentmanagement.webElements.svg.shapes.datatypes.RectangleShape
import interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlock, RenderingInformation}
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer

case class BeBlockUnsupported(beExpressionUnsupported: BeExpressionUnsupported) extends BeBlock {

  override def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {

    val container = RectangleShape
    val text = TextShape(LanguageMap.universalMap(beExpressionUnsupported.originalSource)).addAmends(renderingInfo.factory.defaultTextAmends)
    val res = ShapeAroundShape(container, text) addAmends (renderingInfo.factory.errorColorsAmend)

    NestedBlockRenderer.singleExpressionLineShapeWithInfo(List(), ControlFlowDirected(true, false), res)
  }

}
