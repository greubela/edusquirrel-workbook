package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.control

import it.evadid.vm.types.BeChildRole.ExpressionInSequence
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.*
import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
import it.evadid.core.datastructures.tree.TreeStructureContext
import it.evadid.core.datastructures.tree.nodeImpl.NodeBasedTreePosition
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlock, RenderingInformation}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.tree.BeExpressionNode
import todomove.webElementsOld.webElements.svg.AppSvgElement
import todomove.webElementsOld.webElements.svg.shapes.composite.{BoxManualPositioning, HorizontalAlignment, ManualPositionElement, TableShape, VBoxSameWidth, VerticalAlignment}
import todomove.webElementsOld.webElements.svg.shapes.{BeShape, ControlFlowAndExpressionShape}

import scala.collection.mutable

case class BeBlockSequence(sequence: BeSequence) extends BeBlock {

  override def renderNested(
                             structure: TreeStructureContext[NodeBasedTreePosition, (BeExpressionNode, BeBlock)],
                             childResults: Map[(BeExpressionNode, BeBlock), ControlFlowAndExpressionShape],
                             renderingInfo: RenderingInformation
                           ): ControlFlowAndExpressionShape = {

    val resultList: List[((BeExpressionNode, BeBlock), ControlFlowAndExpressionShape)] = childResults.toList

    val bodyChildrenInOrder = resultList
      .filter(_._1._1.childInfo.myRoleInParent.isInstanceOf[ExpressionInSequence])
      .sortBy(_._1._1.childInfo.myRoleInParent.asInstanceOf[ExpressionInSequence].nr)

    val onlyControlFlow = bodyChildrenInOrder.map(_._2).flatMap(curShape => curShape.onlyControlFlowShape)
    val onlyExpressions = bodyChildrenInOrder.map(_._2).flatMap(curShape => curShape.onlyExpressionShape)
    val fullTable = TableShape(List(onlyControlFlow, onlyExpressions))

    val nested: BoxManualPositioning = new BoxManualPositioning() {
      def calcOffsetsAndDimensions(config: BeRenderingConfig): List[ManualPositionElement] = {
        val columns = List(onlyControlFlow, onlyExpressions)
        val maxColumnWidths = columns.map(_.map(_.displaySize(config).width).maxOption.getOrElse(0.0))

        val res = mutable.ListBuffer[ManualPositionElement]()
        var offsetY: Double = 0
        for (curChild <- bodyChildrenInOrder) {

          if (curChild._2.onlyControlFlowShape.nonEmpty && curChild._2.onlyExpressionShape.nonEmpty) {
            val exprShape = curChild._2.onlyExpressionShape.get
            val controlShape = curChild._2.onlyControlFlowShape.get

            val rowHeight = List(exprShape.displaySize(config).height, controlShape.displaySize(config).height).max
            val leftDim = Dimension[Double](maxColumnWidths.head, rowHeight)
            val rightDim = Dimension[Double](exprShape.displaySize(config).width, rowHeight)

            res.addOne(ManualPositionElement(controlShape, Point[Double](0, offsetY), leftDim))
            res.addOne(ManualPositionElement(exprShape, Point[Double](maxColumnWidths.head, offsetY), rightDim))

            offsetY += rowHeight
          } else {
            val elementDim = curChild._2.displaySize(config)
            res.addOne(ManualPositionElement(curChild._2, Point[Double](0, offsetY), elementDim))
            offsetY += elementDim.height
          }
        }
        res.toList
      }
    }

    new ControlFlowAndExpressionShape {

      override def onlyControlFlowShape: Option[BeShape] = Some(VBoxSameWidth(onlyControlFlow, false, HorizontalAlignment.Left, VerticalAlignment.Top))

      override def onlyExpressionShape: Option[BeShape] = Some(VBoxSameWidth(onlyExpressions, false, HorizontalAlignment.Left, VerticalAlignment.Top))

      override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = nested.displaySize(rendererConfig)

      override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = nested.render(rendererConfig, bounds)
    }


    /*
        val bodyShapes: List[BeShape] = resultList.filter(_._1._1.childPosition.roleInParent == BodySequence(0)).map(_._2)

         ControlFlowAndExpressionShape {
          override def getControlFlowChangeElements: List[ControlFlowShape] = List(ControlFlowProgramStarter(), ControlFlowProgramStopper())

          override def getControlFlowExpressionElements: List[Option[BeShape]] = List(None, None)

          override def getBodiesBetweenControlFlowChanges: List[List[BeShape]] = List(bodyShapes)
        }*/

  }


  def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {

    val bodyChildrenInOrder = renderedChildren
      .filter(_._1.childInfo.myRoleInParent.isInstanceOf[ExpressionInSequence])
      .sortBy(_._1.childInfo.myRoleInParent.asInstanceOf[ExpressionInSequence].nr)

    var res = NestedBlockRenderer.empty()
    for (child <- bodyChildrenInOrder) {
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
