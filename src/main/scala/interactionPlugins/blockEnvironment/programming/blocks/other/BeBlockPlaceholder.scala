package interactionPlugins.blockEnvironment.programming.blocks.other

import contentmanagement.model.vm.code.tree.{BeExpressionNode, BeExtensionPoint}
import interactionPlugins.blockEnvironment.programming.blocks.{BeBlock, NestedBlockRenderer, RenderingInformation}

case class BeBlockPlaceholder(extensionPoint: BeExtensionPoint) extends BeBlock {


  def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {

    val baseColorAmends = if (extensionPoint.isRequired) renderingInfo.factory.errorColorsAmend else renderingInfo.factory.defaultControlColors

    val shape = extensionPoint.extensionMustConformToType
      .createShape
      .addAmends(renderingInfo.factory.acceptingColorsAmend)
      .addAmends(renderingInfo.treeListener.getMouseAmendsForShape(renderingInfo.inProgram, extensionPoint))
      // mouse over does not trigger while dragging???
    NestedBlockRenderer.fromShape(shape)
  }

}
