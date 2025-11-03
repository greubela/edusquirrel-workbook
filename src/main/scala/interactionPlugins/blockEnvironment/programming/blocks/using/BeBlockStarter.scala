package interactionPlugins.blockEnvironment.programming.blocks.using

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.controlStructures.BeSequence
import contentmanagement.model.vm.code.others.BeStartProgram
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.BodySequence
import contentmanagement.webElements.svg.shapes.{BeShape, BeShapeAmendFactory}
import contentmanagement.webElements.svg.shapes.composite.{ShapeAroundShape, VBoxSameWidth}
import contentmanagement.webElements.svg.shapes.datatypes.BeStarterShape
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.editor.elements.BeTreeControllerConfig

case class BeBlockStarter(
                         ) extends BeBlock {


  def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {

    val factory = BeShapeAmendFactory(renderingInfo.renderingConfig)
    val signalAmends = factory.muteOnTreeDragged(renderingInfo.inProgram, renderingInfo.controllerStateVar.signal, factory.defaultControlColors)

    val starterShape = BeStarterShape.addSignalAmends(signalAmends)

    var res =  NestedBlockRenderer.fromShape(starterShape)
    for (curChild <- renderedChildren){
      res = res.addAllLines(curChild._3)
    }
    res
  }

}
