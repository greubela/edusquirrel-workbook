package interactionPlugins.blockEnvironment.programming.blockdisplay.use

import com.raquo.laminar.api.L
import contentmanagement.model.vm.code.controlStructures.*
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.programming.blockdisplay.*
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer

case class BeBlockSequence(sequence: BeSequence) extends BeBlock {

  def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {

    var res = NestedBlockRenderer.empty()
    for (child <- renderedChildren) {
      res = res.withAppendedRenderer(child._3)
    }
    res
  }
  /*
  def renderMain(children: List[(BeExpressionNode, BeShape)], renderingInfo: RenderingInformation): BeShape = {

    val bodySeq = children
      .filter(_._1.childPosition.roleInParent.isInstanceOf[ExpressionInSequence])
      .sortBy(_._1.childPosition.roleInParent.asInstanceOf[ExpressionInSequence].nr)

    TableShape(bodySeq.map(_._2), List(HorizontalAlignment.Left, HorizontalAlignment.Left), List(), false)

    //override def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], rendererConfig: BeRenderingConfig, renderedDisplayChildren: List[(BeBlockReference, BeShape)]): BeShape = {
    val factory = BeShapeAmendFactory(renderingInfo.rendererConfig)
    val signalAmends = factory.muteOnTreeDragged(inProgram, controllerStateVar.signal, factory.defaultControlFlowBackgroundAmend)

    val controlFlowShape = ControlFlowConnectorBackground.addSignalAmends(signalAmends)
    val controlFlows = renderedDisplayChildren.map(_ => controlFlowShape)

    val allChildren = renderedDisplayChildren.zip(controlFlows).flatMap(tup => List(tup._2, tup._1._2))

    println("BeBlockSequence::render -> allChildren:" + allChildren.mkString("\n", "\n  ", ""))
    TableShape(allChildren, List(HorizontalAlignment.Left, HorizontalAlignment.Left), List(), false)
  }
*/
}
