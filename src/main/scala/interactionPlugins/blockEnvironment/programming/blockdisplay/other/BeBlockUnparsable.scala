package interactionPlugins.blockEnvironment.programming.blockdisplay.other

import contentmanagement.model.language.LanguageMap
import contentmanagement.model.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported}
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.webElements.svg.shapes.TextShape
import contentmanagement.webElements.svg.shapes.composite.ShapeAroundShape
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.ControlFlowEmpty
import contentmanagement.webElements.svg.shapes.datatypes.RectangleShape
import interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlock, RenderingInformation}
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer

case class BeBlockUnparsable(beExpressionUnsupported: BeExpressionUnparsable) extends BeBlock {

  override def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {

    val container = RectangleShape
    val text = TextShape(LanguageMap.universalMap(beExpressionUnsupported.originalSource)).addAmends(renderingInfo.factory.defaultTextAmends)
    val res = ShapeAroundShape(container, text) addAmends (renderingInfo.factory.errorColorsAmend)

    NestedBlockRenderer.singleExpressionLineShapeWithInfo(List(), ControlFlowEmpty(), res)
  }

}
