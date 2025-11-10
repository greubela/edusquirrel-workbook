package contentmanagement.webElements.svg.shapes.controlflow.doubleWidth

import contentmanagement.model.geometry.{Dimension, Point}
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.BeShape
import contentmanagement.webElements.svg.shapes.controlflow.ControlFlowConnectorBackground
import contentmanagement.webElements.svg.shapes.decorations.PathUnionOverlay
import interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.PathStatus.*
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.{ControlFlowPath, PathSegment}

case class RepetitionUnion() extends ControlFlowShapeDoubleWidth {

  override def minHeightInSegments: Int = 5

  override def background: BeShape.BeShapeContainerable = ControlFlowConnectorBackground(List((true, true), (true, false)))

  def resumeLastPausedPath(path: ControlFlowPath, newStartPos: Point[Double], curLineHeight: Double, seg: Double): ControlFlowPath = {
    val extraHeight = (curLineHeight - minHeightInSegments * seg).max(0)
    //println("RepetitionUnion::extraHeight" = )
    path.changeLastPathBuilder(_
      .moveToAbs(newStartPos)
      .lineToRel(Dimension(0, 2 * seg))
      .verticalLineWithHeight(extraHeight)
    ).copy(curStatus = HANDLED)
  }

  def finishRepetitionStopPath(path: ControlFlowPath, curLineHeight: Double, renderingInfo: RenderingInformation): (ControlFlowPath, Point[Double]) = {
    val seg: Double = renderingInfo.renderingConfig.controlSegmentSize
    val res = path
      .appendNewSegmentWithLastSegmentPosition(oldPosition => {
        val newPath = SvgPathBuilder[Double](oldPosition)
          .verticalLineWithHeight(seg)
          .lineToRel(Dimension(seg / 2, seg / 2))
          .verticalLineWithHeight(seg * 1.5)
        PathSegment(newPath, List(), false, renderingInfo.renderingConfig.amendFactory.activeFalseConditionControlFlowAmends)
      })
      .copy(curStatus = FINISHED)
    (res, res.lastSegment.curPath.current)
  }

  def combinePaths(firstPath: ControlFlowPath, secondPath: ControlFlowPath, renderingInfo: RenderingInformation): ControlFlowPath = {
    val seg = renderingInfo.renderingConfig.controlSegmentSize
    val newSecond = secondPath.changeLastPathBuilder(builder => builder
      .lineToRel(Dimension(-seg, seg))
    )
    val combiningPath = SvgPathBuilder[Double](newSecond.lastSegment.curPath.current)
      .horizontalLineWithWidth(-2 * seg)
      .horizontalLineWithWidth(-1.5 * seg)
      .lineToRel(Dimension(-seg, -seg))

    val combiningSegment = PathSegment(combiningPath, List(), false, renderingInfo.renderingConfig.amendFactory.inactiveControlFlowAmends.toList)

    ControlFlowPath(FINISHED, newSecond.segments ++ List(combiningSegment) ++ firstPath.segments)
  }


  override def renderControlFlow(cf: ControlFlowOverlayBuilder, renderingInfo: RenderingInformation, centerPoint: Point[Double], curLineHeight: Double): ControlFlowOverlayBuilder = {
    val seg = renderingInfo.renderingConfig.controlSegmentSize

    val (finishedRepetitionStopPath, endPosition): (ControlFlowPath, Point[Double]) = finishRepetitionStopPath(cf.firstOpenPath, curLineHeight, renderingInfo)
    cf
      .changeFirstOpenPath(_ => finishedRepetitionStopPath)
      .unionFirstTwoOpen(combinePaths(_, _, renderingInfo))
      .changeLastPaused(resumeLastPausedPath(_, endPosition, curLineHeight, seg))
      .addDecoration(PathUnionOverlay(), endPosition)

  }
}

