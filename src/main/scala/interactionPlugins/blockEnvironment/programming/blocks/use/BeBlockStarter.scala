package interactionPlugins.blockEnvironment.programming.blocks.use

import com.raquo.laminar.api.L
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.types.*
import contentmanagement.webElements.svg.shapes.BeShapeAmendFactory
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.{ControlFlowProgramStarter, ControlFlowProgramStopper}
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.blocks.NestedBlockRenderer.ControlFlowLine

case class BeBlockStarter(
                         ) extends BeBlock {


  def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {
    val factory = BeShapeAmendFactory(renderingInfo.renderingConfig)
    val signalAmends = factory.muteOnTreeDragged(renderingInfo.inProgram, renderingInfo.controllerStateVar.signal, factory.defaultControlColors)

    val lineStart = NestedBlockRenderer.ControlFlowLine(ControlFlowProgramStarter())
    val lineStop = NestedBlockRenderer.ControlFlowLine(ControlFlowProgramStopper())

    var res = NestedBlockRenderer.empty()
    res = res.withAppendedLine(lineStart)
    for (curChild <- renderedChildren) {
      res = res.withAppendedRenderer(curChild._3)
    }
    res = res.withAppendedLine(lineStop)

    res
  }

}
