package contentmanagement.webElements.svg.shapes.controlflow.doubleWidth
/*


import contentmanagement.model.geometry.{Dimension, Point}
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.BeShape
import contentmanagement.webElements.svg.shapes.controlflow.ControlFlowConnectorBackground
import contentmanagement.webElements.svg.shapes.decorations.{BeDataArrow, PathSplitOverlay}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.PathStatus.*
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.{ControlFlowPath, PathSegment}

case class RepetitionSplitOld() extends ControlFlowShapeDoubleWidth {

  override def minHeightInSegments: Int = 8

  override def background: BeShape.BeShapeContainerable = ControlFlowConnectorBackground(List((true, true), (false, true)))

  private def handleParentPath(path: ControlFlowPath, renderingConfig: BeRenderingConfig, curLineHeight: Double): (ControlFlowPath, Point[Double]) = {
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

  private def startLoopbackPath(startPos: Point[Double], curLineHeight: Double, renderingInfo: RenderingInformation): ControlFlowPath = {
    val seg = renderingInfo.renderingConfig.controlSegmentSize
    val extraHeight = (curLineHeight - seg * minHeightInSegments).max(0)

    val curPath = SvgPathBuilder[Double](startPos)
      .lineToRel(Dimension[Double](-2.5 * seg, 2.5 * seg))
      .lineToRel(Dimension[Double](0, 1.5 * seg))
      .verticalLineWithHeight(extraHeight / 2)
    val segment = PathSegment(curPath, List(), false, renderingInfo.renderingConfig.amendFactory.inactiveControlFlowAmends)
    ControlFlowPath(HANDLED, List(segment))
  }

  private def startAcceptingPath(startPos: Point[Double], curLineHeight: Double, renderingInfo: RenderingInformation): ControlFlowPath = {
    val seg = renderingInfo.renderingConfig.controlSegmentSize
    val extraHeight = (curLineHeight - seg * minHeightInSegments).max(0)

    val curPath = SvgPathBuilder[Double](startPos)
      .lineToRel(Dimension[Double](3 * seg, 3 * seg))
      .lineToRel(Dimension[Double](0, seg))
      .verticalLineWithHeight(extraHeight / 2)

    val segment = PathSegment(curPath, List(), false, renderingInfo.renderingConfig.amendFactory.trueConditionControlFlowAmends)
    ControlFlowPath(HANDLED, List(segment))
  }

  private def startStopRepetitionPath(startPos: Point[Double], curLineHeight: Double, renderingInfo: RenderingInformation): ControlFlowPath = {
    val seg = renderingInfo.renderingConfig.controlSegmentSize
    val extraHeight = (curLineHeight - seg * minHeightInSegments).max(0)

    val curPathRed = SvgPathBuilder[Double](startPos)
      .lineToRel(Dimension[Double](-3.5 * seg, 0))
      .lineToRel(Dimension[Double](0, 3 * seg))

    val curPathInactive = SvgPathBuilder[Double](curPathRed.current)
      .verticalLineWithHeight(seg)
      .verticalLineWithHeight(extraHeight / 2)

    val segment1 = PathSegment(curPathRed, List(), false, renderingInfo.renderingConfig.amendFactory.falseConditionControlFlowAmends)
    val segment2 = PathSegment(curPathInactive, List(), false, renderingInfo.renderingConfig.amendFactory.inactiveControlFlowAmends)
    ControlFlowPath(HANDLED, List(segment1, segment2))
  }


  override def renderControlFlow(cf: ControlFlowOverlayBuilder, renderingInfo: RenderingInformation, centerPoint: Point[Double], curLineHeight: Double): ControlFlowOverlayBuilder = {
    val seg = renderingInfo.renderingConfig.controlSegmentSize

    val (changedParent, parentEndPos): (ControlFlowPath, Point[Double]) = handleParentPath(cf.firstOpenPath, renderingInfo.renderingConfig, curLineHeight)
    cf
      .changeFirstOpenPath(_ => changedParent)
      // start the accepting/repetition path
      .startNewPath(startStopRepetitionPath(parentEndPos, curLineHeight, renderingInfo))
      // loopback path
      .startNewPath(startLoopbackPath(parentEndPos, curLineHeight, renderingInfo))
      // active path
      .startNewPath(startAcceptingPath(parentEndPos, curLineHeight, renderingInfo))
      .addDecoration(PathSplitOverlay(), centerPoint)
      .addDecoration(BeDataArrow(), centerPoint.moveWithDimension(Dimension[Double](3.0 * seg, 0)))
  }
}*/