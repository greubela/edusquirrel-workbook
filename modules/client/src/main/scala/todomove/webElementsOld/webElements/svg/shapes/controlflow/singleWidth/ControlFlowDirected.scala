package todomove.webElementsOld.webElements.svg.shapes.controlflow.singleWidth

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{lineHeight, svg}
import ControlFlowDirected.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.PathStatus.HANDLED
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.{ControlFlowPath, PathSegment}
import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder
import todomove.webElementsOld.webElements.svg.AppSvgElement
import todomove.webElementsOld.webElements.svg.compositeElements.AppDecoratedSvgElement
import todomove.webElementsOld.webElements.svg.shapes.BeShapeDecoration
import todomove.webElementsOld.webElements.svg.shapes.composite.{HorizontalAlignment, ShapeStack, VerticalAlignment}
import todomove.webElementsOld.webElements.svg.shapes.decorations.ControlArrowUpDown

import scala.collection.mutable

case class ControlFlowDirected(goesDown: Boolean, isActive: Boolean = false) extends ControlFlowShapeSingleWidth {

  override def minHeightInSegments: Int = 2

  private def startNewSegment(startPos: Point[Double], newPathAmends: Seq[L.Modifier[L.SvgElement]]): ControlFlowPath = {
    val curPath = SvgPathBuilder[Double](startPos)
    val segment = PathSegment(curPath, List(), false, newPathAmends)
    ControlFlowPath(HANDLED, List(segment))
  }

  def handleParentPath(path: ControlFlowPath, renderingConfig: BeRenderingConfig, centerPoint: Point[Double], curLineHeight: Double): (ControlFlowPath, List[Point[Double]]) = {
    val seg = renderingConfig.controlSegmentSize

    // todo: change path if active <-> inactive mismatch

    val fullArrowHeight: Int = (seg * 4).toInt
    val arrowNr = curLineHeight.toInt / fullArrowHeight

    val resArrowPositions = mutable.ListBuffer[Point[Double]]()

    var res = path
     // Markierung Anfang
    res = res.changeLastPathBuilder(_
      .moveToRel(Dimension(-5.0, 0.0))
      .horizontalLineWithWidth(10.0)
      .moveToRel(Dimension(-5.0, 0.0))
    )

    val shapeStartY: Double = centerPoint.y - curLineHeight / 2
    var movedY: Double = 0

    for (i <- 0 until arrowNr) {
      val arrowShapeHeight: Double = seg / 5.0 * 9.0
      val distToArrow = seg / 5.0 * 1.0
      val heightForLines = fullArrowHeight - arrowShapeHeight - distToArrow
      res = res.changeLastPathBuilder(_
        .verticalLineWithHeight(heightForLines / 2)
        .moveToRel(Dimension(0, arrowShapeHeight + 2 * distToArrow))
        .verticalLineWithHeight(heightForLines / 2)
      )
      val curArrowY = shapeStartY + movedY + heightForLines / 2 + distToArrow + arrowShapeHeight / 2
      resArrowPositions += Point(centerPoint.x, curArrowY)
      movedY += heightForLines + arrowShapeHeight + 2 * distToArrow

    }

    val yToGo: Double = curLineHeight - movedY
    res = res.changeLastPathBuilder(_.verticalLineWithHeight(yToGo))

     // Markierung Ende
    res = res.changeLastPathBuilder(_
      .moveToRel(Dimension(-5.0, 0.0))
      .horizontalLineWithWidth(10.0)
      .moveToRel(Dimension(-5.0, 0.0))
    )
    (res, resArrowPositions.toList)
  }

  override def renderControlFlow(cf: ControlFlowOverlayBuilder, renderingInfo: RenderingInformation, centerPoint: Point[Double], curLineHeight: Double): ControlFlowOverlayBuilder = {
    var pathRes: ControlFlowPath = cf.firstOpenPath
    if (cf.firstOpenPath.lastSegment.isActive != isActive) {
      val amends = if (isActive) pathRes.firstSegment.pathAmends else renderingInfo.renderingConfig.amendFactory.inactiveControlFlowAmends
     // pathRes = pathRes.continueWithNewSegment(isActive, amends)
    }
    val (handledPath, arrowCenters) = handleParentPath(pathRes, renderingInfo.renderingConfig, centerPoint, curLineHeight)
    pathRes = handledPath

    var builderRes = cf.changeFirstOpenPath(_ => pathRes)
    for(curArrowCenter <- arrowCenters) {
      builderRes = builderRes.addDecoration(ControlArrowUpDown(goesDown, isActive), curArrowCenter)
    }
    builderRes
  }


}
