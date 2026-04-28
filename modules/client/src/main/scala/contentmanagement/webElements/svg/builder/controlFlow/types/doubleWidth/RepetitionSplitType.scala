package contentmanagement.webElements.svg.builder.controlFlow.types.doubleWidth

import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.builder.controlFlow.path.{ControlFlowPath, ControlFlowPathOverlay, ControlFlowPathSegment, PathStatus, PathType, SegmentType}
import contentmanagement.webElements.svg.shapes.BeShape.BeShapeContainerable
import contentmanagement.webElements.svg.shapes.controlflow.ControlFlowConnectorBackground
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.core.datastructures.geometry.{Dimension, Point}

case class RepetitionSplitType() extends ControlFlowTypeDoubleWidth {

  override def minHeightInSegments: Int = 8

  override def backgroundShape: BeShapeContainerable = ControlFlowConnectorBackground(List((true, true), (false, true)))

  override def renderPaths(renderingConfig: BeRenderingConfig, oldOverlay: ControlFlowPathOverlay): ControlFlowPathOverlay = {
    val seg = renderingConfig.controlSegmentSize
    val curLineHeight = seg * minHeightInSegments
    val extraHeight = (curLineHeight - seg * minHeightInSegments).max(0)

    oldOverlay.lastPathByStatusAndType(PathStatus.OPEN, PathType.BASE) match {
      case Some((parentPath, parentIndex)) =>
        var resPath = parentPath.segments.last.curPath
          .verticalLineWithHeight(extraHeight / 2.0)
          .verticalLineWithHeight(1 * seg)
          .horizontalLineWithWidth(3 * seg)

        val unionCenter = resPath.current.moveWithDimension(Dimension[Double](0, seg))

        resPath = resPath.verticalLineWithHeight(3 * seg)

        val updatedParent = parentPath.copy(
          curStatus = PathStatus.PAUSED,
          segments = parentPath.segments.init :+ parentPath.segments.last.copy(curPath = resPath)
        )
        val parentEndPos = updatedParent.segments.last.curPath.current

        val stopPath = {
          val curPathRed = SvgPathBuilder[Double](parentEndPos)
            .lineToRel(Dimension[Double](-3.5 * seg, 3.5 * seg))
            .verticalLineWithHeight(0.5 * seg)
          val curPathInactive = SvgPathBuilder[Double](curPathRed.current)
            .verticalLineWithHeight(extraHeight / 2)
          ControlFlowPath(
            PathStatus.HANDLED,
            PathType.CONDITION_FALSE,
            List(
              ControlFlowPathSegment(curPathRed, SegmentType.FALSE_SEGMENT_ACTIVE),
              ControlFlowPathSegment(curPathInactive, SegmentType.FALSE_SEGMENT_INACTIVE)
            )
          )
        }

        val loopbackPath = {
          val curPath = SvgPathBuilder[Double](unionCenter)
            .horizontalLineWithWidth(-2.5 * seg)
            .verticalLineWithHeight(extraHeight / 2.0)
            .verticalLineWithHeight(6.0 * seg)
          ControlFlowPath(PathStatus.HANDLED, PathType.RETURNING_PATH, List(ControlFlowPathSegment(curPath, SegmentType.RETURN_SEGMENT)))
        }

        val acceptingPath = {
          val curPath = SvgPathBuilder[Double](parentEndPos)
            .lineToRel(Dimension[Double](3 * seg, 3 * seg))
            .lineToRel(Dimension[Double](0, seg))
            .verticalLineWithHeight(extraHeight / 2)
          ControlFlowPath(PathStatus.HANDLED, PathType.CONDITION_TRUE, List(ControlFlowPathSegment(curPath, SegmentType.TRUE_SEGMENT_ACTIVE)))
        }

        oldOverlay
          .replacePath(parentIndex, updatedParent, setToHandled = false)
          .addPath(stopPath)
          .addPath(loopbackPath)
          .addPath(acceptingPath)

      case None => oldOverlay
    }
  }
}
