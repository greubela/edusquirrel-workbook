package interactionPlugins.blockEnvironment.programming.blockdisplay.other

import contentmanagement.model.vm.code.errors.BeSingleLineComment
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.webElements.svg.shapes.{BeShape, ControlFlowShape, TextShape}
import contentmanagement.webElements.svg.shapes.composite.ShapeAroundShape
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.*
import contentmanagement.webElements.svg.shapes.datatypes.{BeErrorShape, RectangleShape}
import contentmanagement.webElements.svg.shapes.special.CommandShape
import interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlock, BeBlockSingleShape, RenderingInformation}
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer

case class BeBlockComment(comment: BeSingleLineComment) extends BeBlockSingleShape {

  override def renderShape(childrenShapes: List[(BeExpressionNode, BeShape)], renderingInformation: RenderingInformation): (ControlFlowShape, BeShape) = {
    val container = CommandShape()
    val text = TextShape(comment.commentStr).addAmends(renderingInformation.factory.defaultTextAmends)

    val res = ShapeAroundShape(container, text)
      .addSignalAmends(renderingInformation.factory.muteOnTreeDragged(renderingInformation.inProgram, renderingInformation.editorState.controllerStateVar.signal, renderingInformation.factory.defaultControlColors))

    (ControlFlowDirected(true, false), res)
  }
}
