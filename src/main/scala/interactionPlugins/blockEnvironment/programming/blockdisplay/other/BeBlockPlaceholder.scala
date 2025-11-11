package interactionPlugins.blockEnvironment.programming.blockdisplay.other

import contentmanagement.model.vm.code.tree.{BeExpressionNode, BeExtensionPoint}
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.*
import interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlock, RenderingInformation}
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer

case class BeBlockPlaceholder(extensionPoint: BeExtensionPoint) extends BeBlock {


  def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {

    val baseColorAmends = if (extensionPoint.isRequired) renderingInfo.factory.errorColorsAmend else renderingInfo.factory.defaultControlColors

    //todo: why so many children?
    val res = extensionPoint.extensionMustConformToType // todo: extension will be interpreted as (and then reverse whether possible)
      .createShape
      .addAmends(renderingInfo.factory.acceptingColorsAmend)
      .addAmends(renderingInfo.treeListener.getMouseAmendsForShape(renderingInfo.inProgram, extensionPoint))
      .addOnRendering(bounds => println("BeBlockPlaceholder(" + extensionPoint + "): " + bounds))
      // mouse over does not trigger while dragging???
    NestedBlockRenderer.singleExpressionLineShapeWithInfo(List(), ControlFlowDirected(true, true), res)
  }

}
