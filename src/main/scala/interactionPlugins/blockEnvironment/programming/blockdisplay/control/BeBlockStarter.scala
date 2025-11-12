package interactionPlugins.blockEnvironment.programming.blockdisplay.control

import contentmanagement.datastructures.tree.TreeStructureContext
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.types.BeChildRole.BodySequence
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.shapes.composite.*
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.{ControlFlowProgramStarter, ControlFlowProgramStopper}
import contentmanagement.webElements.svg.shapes.{BeShape, BeShapeAmendFactory, ControlFlowAndExpressionShape, ControlFlowShape}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.*
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer.ControlFlowLine

case class BeBlockStarter(
                         ) extends BeBlock {

  override def renderNested(
                             structure: TreeStructureContext[NodeBasedTreePosition, (BeExpressionNode, BeBlock)],
                             childResults: Map[(BeExpressionNode, BeBlock), ControlFlowAndExpressionShape],
                             renderingInfo: RenderingInformation
                           ): ControlFlowAndExpressionShape = {

    val resultList: List[((BeExpressionNode, BeBlock), ControlFlowAndExpressionShape)] = childResults.toList
    val bodyShapes: List[BeShape] = resultList.filter(_._1._1.childPosition.roleInParent == BodySequence(0)).map(_._2)

    
    val starter = ControlFlowProgramStarter()
    val stopper = ControlFlowProgramStopper()
    val bodyShape = VBoxSameWidth(bodyShapes, false, HorizontalAlignment.Left, VerticalAlignment.Center)

    val children = List(starter, bodyShape, stopper)
    val box = PlainVBox(children)
    
    new ControlFlowAndExpressionShape {

      override def onlyControlFlowShape: Option[BeShape] = None
      
      override def onlyExpressionShape: Option[BeShape] = None

      override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = box.displaySize(rendererConfig)

      override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = box.render(rendererConfig, bounds)
    }


  }

  def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {
    val factory = BeShapeAmendFactory(renderingInfo.renderingConfig)
    val signalAmends = factory.muteOnTreeDragged(renderingInfo.inProgram, renderingInfo.editorState.controllerStateVar.signal, factory.defaultControlColors)

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
