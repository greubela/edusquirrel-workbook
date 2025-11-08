package interactionPlugins.blockEnvironment.programming.blockdisplay.other

import contentmanagement.model.vm.code.errors.BeSingleLineComment
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.webElements.svg.shapes.TextShape
import contentmanagement.webElements.svg.shapes.composite.ShapeAroundShape
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.*
import contentmanagement.webElements.svg.shapes.datatypes.{BeErrorShape, RectangleShape}
import interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlock, RenderingInformation}
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer

case class BeBlockComment(comment: BeSingleLineComment) extends BeBlock {

  override def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {
    val container = RectangleShape
    val text = TextShape(comment.commentStr).addAmends(renderingInfo.factory.defaultTextAmends)

    val res = ShapeAroundShape(container, text).addSignalAmends(renderingInfo.factory.muteOnTreeDragged(renderingInfo.inProgram, renderingInfo.controllerStateVar.signal, renderingInfo.factory.defaultControlColors))

    NestedBlockRenderer.singleExpressionLineShapeWithInfo(List(), ControlFlowEmpty(), res)
  }
}
