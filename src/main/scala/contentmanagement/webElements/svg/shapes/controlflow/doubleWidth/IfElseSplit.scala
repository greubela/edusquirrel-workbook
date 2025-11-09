package contentmanagement.webElements.svg.shapes.controlflow.doubleWidth

import com.raquo.laminar.api.L
import contentmanagement.model.geometry.{Dimension, Point}
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.*
import contentmanagement.webElements.svg.shapes.composite.VerticalAlignment
import contentmanagement.webElements.svg.shapes.controlflow.ControlFlowConnectorBackground
import contentmanagement.webElements.svg.shapes.controlflow.doubleWidth.IfElseSplit.*
import contentmanagement.webElements.svg.shapes.decorations.{BeDataArrow, PathSplitOverlay}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.PathStatus.{OPEN, PAUSED}
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.{ControlFlowPath, PathSegment}

case class IfElseSplit() extends ControlFlowShapeDoubleWidth {

  override def minHeightInSegments: Int = 8

  override def background: BeShape.BeShapeContainerable = ControlFlowConnectorBackground(List((true, true), (false, true)))

  private def handleParentPath(path: ControlFlowPath, renderingConfig: BeRenderingConfig, curLineHeight: Double): (ControlFlowPath, Point[Double]) = {

    val seg = renderingConfig.controlSegmentSize
    val extraHeight = (curLineHeight - seg * 7).max(0)

    val res = path.changeLastPathBuilder(_
      .verticalLineWithHeight(extraHeight / 2)
      .lineToRel(Dimension[Double](0, seg))
      .lineToRel(Dimension(3 * seg, 0))
      .lineToRel(Dimension(0, 2 * seg))
    )

    (res.copy(curStatus = PAUSED), res.lastSegment.curPath.current)
  }

  private def startNewPath(startPos: Point[Double], curLineHeight: Double, seg: Double, toTheRight: Boolean, newPathAmends: Seq[L.Modifier[L.SvgElement]]): ControlFlowPath = {
    val extraHeight = (curLineHeight - seg * 7).max(0)
    val xDirection = if (toTheRight) 3 * seg else -3 * seg

    val curPath = SvgPathBuilder[Double](startPos)
      .lineToRel(Dimension[Double](xDirection, 3 * seg))
      .lineToRel(Dimension[Double](0, seg))
      .verticalLineWithHeight(extraHeight / 2)
    val segment = PathSegment(curPath, List(), false, newPathAmends)
    ControlFlowPath(OPEN, List(segment))
  }

  override def renderControlFlow(cf: ControlFlowOverlayBuilder, renderingInfo: RenderingInformation, centerPoint: Point[Double], curLineHeight: Double): ControlFlowOverlayBuilder = {
    val seg = renderingInfo.renderingConfig.controlSegmentSize

    val (changedFirst, parentEndPos): (ControlFlowPath, Point[Double]) = handleParentPath(cf.firstOpenPath, renderingInfo.renderingConfig, curLineHeight)
    cf
      .changeFirstOpenPath(_ => changedFirst)
      .startNewPath(startNewPath(parentEndPos, curLineHeight, seg, false, renderingInfo.renderingConfig.amendFactory.inactiveControlFlowAmends))
      .startNewPath(startNewPath(parentEndPos, curLineHeight, seg, true, renderingInfo.renderingConfig.amendFactory.trueConditionControlFlowAmends))
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

