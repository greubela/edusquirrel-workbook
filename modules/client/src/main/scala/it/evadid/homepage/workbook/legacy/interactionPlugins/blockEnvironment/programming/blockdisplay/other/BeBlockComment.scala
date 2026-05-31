package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.other

import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlock, BeBlockSingleShape, RenderingInformation}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer
import todomove.datastructures.core.vm.code.errors.BeSingleLineComment
import todomove.datastructures.core.vm.code.tree.BeExpressionNode
import todomove.webElementsOld.webElements.svg.shapes.{BeShape, ControlFlowShape, TextShape}
import todomove.webElementsOld.webElements.svg.shapes.composite.ShapeAroundShape
import todomove.webElementsOld.webElements.svg.shapes.controlflow.singleWidth.ControlFlowDirected
import todomove.webElementsOld.webElements.svg.shapes.datatypes.{BeErrorShape, RectangleShape}
import todomove.webElementsOld.webElements.svg.shapes.special.{CommandShape, CommentShape}

case class BeBlockComment(comment: BeSingleLineComment) extends BeBlockSingleShape {

  override def renderShape(childrenShapes: List[(BeExpressionNode, BeShape)], renderingInformation: RenderingInformation): (ControlFlowShape, BeShape) = {
    val container = CommentShape()
    val text = TextShape(comment.commentStr).addAmends(renderingInformation.factory.defaultTextAmends)

    val res = ShapeAroundShape(container, text)
      .addSignalAmends(renderingInformation.factory.muteOnTreeDragged(renderingInformation.inProgram, renderingInformation.editorState.controllerStateVar.signal, renderingInformation.factory.defaultControlColors))

    (ControlFlowDirected(true, false), res)
  }
}
