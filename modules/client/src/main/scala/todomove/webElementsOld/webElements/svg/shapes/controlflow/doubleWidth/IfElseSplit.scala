package todomove.webElementsOld.webElements.svg.shapes.controlflow.doubleWidth

import com.raquo.laminar.api.L
import todomove.webElementsOld.webElements.svg.shapes.*
import IfElseSplit.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.PathStatus.{HANDLED, OPEN, PAUSED}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.{ControlFlowPath, PathSegment}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.PathStatus.*
import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder
import todomove.webElementsOld.webElements.svg.shapes.BeShape
import todomove.webElementsOld.webElements.svg.shapes.controlflow.ControlFlowConnectorBackground
import todomove.webElementsOld.webElements.svg.shapes.decorations.{BeDataArrow, PathSplitOverlay}

case class IfElseSplit() extends ControlFlowShapeDoubleWidth {

  override def minHeightInSegments: Int = 8

  override def background: BeShape.BeShapeContainerable = ControlFlowConnectorBackground(List((true, true), (false, true)))

  private def handleParentPath(path: ControlFlowPath, renderingConfig: BeRenderingConfig, curLineHeight: Double): (ControlFlowPath, Point[Double]) = {

    /* todo : sanftere kanten?
    val seg = renderingConfig.controlSegmentSize
    val extraHeight = (curLineHeight - seg * 7).max(0)

    val res = path.changeLastPathBuilder(_
      .verticalLineWithHeight(extraHeight / 2)
      //
      .verticalLineWithHeight(seg)
      .lineToRel(Dimension[Double](seg/2, seg/2))
      .horizontalLineWithWidth(2*seg)
      .lineToRel(Dimension(seg/2, seg/2))
      .verticalLineWithHeight(seg)

    )

    (res.copy(curStatus = PAUSED), res.lastSegment.curPath.current)*/

    val seg = renderingConfig.controlSegmentSize
    val extraHeight = (curLineHeight - seg * minHeightInSegments).max(0)
    val res = path.changeLastPathBuilder(_
      .verticalLineWithHeight(extraHeight / 2)
      .lineToRel(Dimension[Double](0, seg))
      .lineToRel(Dimension(3 * seg, 0))
      .lineToRel(Dimension(0, 3 * seg))
    )
    (res.copy(curStatus = PAUSED), res.lastSegment.curPath.current)
  }

  private def startIfPath(startPos: Point[Double], curLineHeight: Double, renderingInfo: RenderingInformation): ControlFlowPath = {
    val seg = renderingInfo.renderingConfig.controlSegmentSize
    val extraHeight = (curLineHeight - seg * minHeightInSegments).max(0)

    val curPath = SvgPathBuilder[Double](startPos)
      .lineToRel(Dimension[Double](3 * seg, 3 * seg))
      .lineToRel(Dimension[Double](0, seg))
      .verticalLineWithHeight(extraHeight / 2)
    val segment = PathSegment(curPath, List(), false, renderingInfo.renderingConfig.amendFactory.activeTrueConditionControlFlowAmends)
    ControlFlowPath(OPEN, List(segment))
  }

  private def startElsePath(startPos: Point[Double], curLineHeight: Double, renderingInfo: RenderingInformation): ControlFlowPath = {
    val seg = renderingInfo.renderingConfig.controlSegmentSize
    val extraHeight = (curLineHeight - seg * minHeightInSegments).max(0)

    val curPathRed = SvgPathBuilder[Double](startPos)
      .lineToRel(Dimension[Double](-3 * seg, 3 * seg))
      .lineToRel(Dimension[Double](0, seg))
      .verticalLineWithHeight(extraHeight / 2)

    val curPathInactive = SvgPathBuilder[Double](curPathRed.current)

    val segment1 = PathSegment(curPathRed, List(), false, renderingInfo.renderingConfig.amendFactory.activeFalseConditionControlFlowAmends)
    val segment2 = PathSegment(curPathInactive, List(), false, renderingInfo.renderingConfig.amendFactory.inactiveFalseConditionControlFlowAmends)
    ControlFlowPath(HANDLED, List(segment1, segment2))
  }

  override def renderControlFlow(cf: ControlFlowOverlayBuilder, renderingInfo: RenderingInformation, centerPoint: Point[Double], curLineHeight: Double): ControlFlowOverlayBuilder = {
    val seg = renderingInfo.renderingConfig.controlSegmentSize

    val (changedFirst, parentEndPos): (ControlFlowPath, Point[Double]) = handleParentPath(cf.firstOpenPath, renderingInfo.renderingConfig, curLineHeight)
    cf
      .changeFirstOpenPath(_ => changedFirst)
      .startNewPath(startElsePath(parentEndPos, curLineHeight, renderingInfo))
      .startNewPath(startIfPath(parentEndPos, curLineHeight, renderingInfo))
      .addDecoration(PathSplitOverlay(), centerPoint)
      .addDecoration(BeDataArrow(), centerPoint.moveWithDimension(Dimension[Double](3.0 * seg, 0)))
  }


  /*override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {

    val bwa = background.addAmends(rendererConfig.amendFactory.defaultControlColors)
    val mwa = MoveControlFlowToCenter().addAmends(rendererConfig.amendFactory.activeControlFlowAmends)
    val rwa = RightPathTrueOverlay().addAmends(rendererConfig.amendFactory.trueConditionControlFlowAmends)
    val lwa = LeftPathFalseOverlay().addAmends(rendererConfig.amendFactory.falseConditionControlFlowAmends)
    val twa = IfElseConditionOverlay().addAmends(rendererConfig.amendFactory.splitSymbolControlFlowAmends)

    val awa =

    val noOffset: Point[Double] = Point[Double](0, 0)
    val overlays: List[BeShape] = List(bwa, mwa, rwa, lwa, awa)

    val arrowLeft = rendererConfig.controlSegmentSize * 9.0
    val arrowTop = rendererConfig.controlSegmentSize * 3.0 + rendererConfig.controlSegmentSize / 5.0 * 1.0

    val stack = ShapeStack(List(lwa, rwa, mwa, twa, awa), HorizontalAlignment.Left, VerticalAlignment.Top, Map(awa -> Point[Double](arrowLeft, arrowTop)))

    val bR = bwa.render(rendererConfig, bounds)
    val sR = stack.render(rendererConfig, bounds)
    AppDecoratedSvgElement(bR, List(sR), List())

  }*/

}

