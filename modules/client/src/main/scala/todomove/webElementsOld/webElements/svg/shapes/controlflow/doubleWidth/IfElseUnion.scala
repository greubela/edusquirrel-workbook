package todomove.webElementsOld.webElements.svg.shapes.controlflow.doubleWidth

import IfElseUnion.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.ControlFlowPath
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.PathStatus.{FINISHED, HANDLED, OPEN}
import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder
import todomove.webElementsOld.webElements.svg.AppSvgElement
import todomove.webElementsOld.webElements.svg.builder.SvgPathBuilder
import todomove.webElementsOld.webElements.svg.compositeElements.AppDecoratedSvgElement
import todomove.webElementsOld.webElements.svg.shapes.{BeShape, BeShapeDecoration}
import todomove.webElementsOld.webElements.svg.shapes.composite.{HorizontalAlignment, ShapeStack, VerticalAlignment}
import todomove.webElementsOld.webElements.svg.shapes.controlflow.ControlFlowConnectorBackground
import todomove.webElementsOld.webElements.svg.shapes.decorations.{ControlArrowUpDown, PathCrossOverlay, PathUnionOverlay}

case class IfElseUnion() extends ControlFlowShapeDoubleWidth {


  override def background: BeShape.BeShapeContainerable = ControlFlowConnectorBackground(List((true, true), (true, false)))


  override def minHeightInSegments: Int = 7

  def finishParentPath(path: ControlFlowPath, curLineHeight: Double, seg: Double, toTheRight: Boolean): (ControlFlowPath, Point[Double]) = {
    val xDirection = if (toTheRight) 3 * seg else -3 * seg
    val res = path.changeLastPathBuilder(
      _.lineToRel(Dimension(xDirection, 3 * seg))
    )
    (res.copy(curStatus = FINISHED), res.lastSegment.curPath.current)
  }

  def resumeLastPausedPath(path: ControlFlowPath, newStartPos: Point[Double], curLineHeight: Double, seg: Double): ControlFlowPath = {
    val extraHeight = (curLineHeight - seg * 7).max(0)

    path.changeLastPathBuilder(_
      .moveToAbs(newStartPos)
      .lineToRel(Dimension(0, 2 * seg))
      .horizontalLineWithWidth(-3 * seg)
      .verticalLineWithHeight(2 * seg)
      .verticalLineWithHeight(extraHeight)
    ).copy(curStatus = HANDLED)

  }

  override def renderControlFlow(cf: ControlFlowOverlayBuilder, renderingInfo: RenderingInformation, centerPoint: Point[Double], curLineHeight: Double): ControlFlowOverlayBuilder = {
    val seg = renderingInfo.renderingConfig.controlSegmentSize

    var res = cf

    val (changedFirst, parentEndPos1): (ControlFlowPath, Point[Double]) = finishParentPath(res.firstOpenPath, curLineHeight, seg, true)
    res = res.changeFirstOpenPath(_ => changedFirst)

    val (changedSecond, parentEndPos2): (ControlFlowPath, Point[Double]) = finishParentPath(res.firstOpenPath, curLineHeight, seg, false)
    res = res.changeFirstOpenPath(_ => changedSecond)

    if (parentEndPos1 != parentEndPos2) {
      println("[WARN] IfElseUnion::renderControlFlow: parentEndPos don´t match for some reason: " + parentEndPos1 + " != " + parentEndPos2)
    }

    res = res.changeLastPaused(resumeLastPausedPath(_, parentEndPos1, curLineHeight, seg))
    res = res.addDecoration(PathUnionOverlay(), centerPoint.moveWithDimension(Dimension(0, -seg)))
    res = res.addDecoration(ControlArrowUpDown(true), centerPoint.moveWithDimension(Dimension(0, -seg)))

    res
  }


  /*override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {

    val bwa = background.addAmends(rendererConfig.amendFactory.defaultControlColors)
    val rwa = RightPathToCenter().addAmends(rendererConfig.amendFactory.activeControlFlowAmends)
    val lwa = LeftPathToCenter().addAmends(rendererConfig.amendFactory.inactiveControlFlowAmends)
    val mwa = MoveControlFlowToLeft().addAmends(rendererConfig.amendFactory.activeControlFlowAmends)
    val pwa = PathUnionOverlay().addAmends(rendererConfig.amendFactory.combineSymbolControlFlowAmends)

    val stack = ShapeStack(List(lwa, rwa, mwa, pwa), HorizontalAlignment.Left, VerticalAlignment.Top)

    val bR = bwa.render(rendererConfig, bounds)
    val sR = stack.render(rendererConfig, bounds)
    AppDecoratedSvgElement(bR, List(sR), List())
  }*/


}

object IfElseUnion {
  /*
    case class RightPathToCenter() extends BeShapeDecoration {
      override def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = {
        val seg = rendererConfig.controlSegmentSize
        SvgPathBuilder(bounds.startPoint)
          .moveToRel(Dimension(9 * seg, 0))
          .lineToRel(Dimension(0, seg))
          .lineToRel(Dimension(-3 * seg, 3 * seg))
      }
    }

    case class LeftPathToCenter() extends BeShapeDecoration {
      override def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = {
        val seg = rendererConfig.controlSegmentSize
        SvgPathBuilder(bounds.startPoint)
          .moveToRel(Dimension(3 * seg, 0))
          .lineToRel(Dimension(0, seg))
          .lineToRel(Dimension(3 * seg, 3 * seg))
      }
    }

    case class MoveControlFlowToLeft() extends BeShapeDecoration {
      override def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = {
        val seg = rendererConfig.controlSegmentSize
        SvgPathBuilder(bounds.startPoint)
          .moveToRel(Dimension(6 * seg, 5 * seg))
          .lineToRel(Dimension(0, seg))
          .lineToRel(Dimension(-3 * seg, 0))
          .lineToRel(Dimension(0, seg))
      }
    }


  */
}