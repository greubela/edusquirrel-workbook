package interactionPlugins.blockEnvironment.programming.blockdisplay.other

import contentmanagement.webElements.svg.shapes.{BeShape, ControlFlowShape, TextShape}
import contentmanagement.webElements.svg.shapes.composite.ShapeAroundShape
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.ControlFlowEmpty
import contentmanagement.webElements.svg.shapes.datatypes.RectangleShape
import datastructures.core.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported}
import datastructures.core.vm.code.tree.BeExpressionNode
import interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlock, BeBlockSingleShape, RenderingInformation}
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer
import it.evadid.core.datastructures.language.LanguageMap

case class BeBlockUnparsable(beExpressionUnsupported: BeExpressionUnparsable) extends BeBlockSingleShape {

  override def renderShape(childrenShapes: List[(BeExpressionNode, BeShape)], renderingInformation: RenderingInformation): (ControlFlowShape, BeShape) = {

    val container = RectangleShape
    val text = TextShape(LanguageMap.universalMap(beExpressionUnsupported.originalSource)).addAmends(renderingInformation.factory.defaultTextAmends)
    val res = ShapeAroundShape(container, text).addAmends(renderingInformation.factory.errorColorsAmend)

    (ControlFlowEmpty(), res)
  }

}
