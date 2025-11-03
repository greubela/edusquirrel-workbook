package interactionPlugins.blockEnvironment.programming.blocks.other

import contentmanagement.model.vm.code.errors.BeSingleLineComment
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.webElements.svg.shapes.TextShape
import contentmanagement.webElements.svg.shapes.composite.ShapeAroundShape
import contentmanagement.webElements.svg.shapes.datatypes.{BeErrorShape, RectangleShape}
import interactionPlugins.blockEnvironment.programming.blocks.{BeBlock, NestedBlockRenderer, RenderingInformation}

case class BeBlockComment(comment: BeSingleLineComment) extends BeBlock {

  override def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {
    val container = RectangleShape
    val text = TextShape(comment.commentStr).addAmends(renderingInfo.factory.defaultTextAmends)

    val res = ShapeAroundShape(container, text).addSignalAmends(renderingInfo.factory.muteOnTreeDragged(renderingInfo.inProgram, renderingInfo.controllerStateVar.signal, renderingInfo.factory.defaultControlColors))

    NestedBlockRenderer.fromShape(res)
  }
}
