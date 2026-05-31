package todomove.webElementsOld.webElements.svg.builder.controlFlow.types.doubleWidth

import todomove.webElementsOld.webElements.svg.shapes.BeShape.BeShapeContainerable
import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.builder.SvgPathBuilder
import todomove.webElementsOld.webElements.svg.builder.controlFlow.path.{ControlFlowPath, ControlFlowPathOverlay, ControlFlowPathSegment, PathStatus, PathType, SegmentType}
import todomove.webElementsOld.webElements.svg.shapes.controlflow.ControlFlowConnectorBackground

case class IfElseSplitType() extends ControlFlowTypeDoubleWidth {

  override def minHeightInSegments: Int = 8

  override def backgroundShape: BeShapeContainerable =
    ControlFlowConnectorBackground(List((true, true), (false, true)))

  override def renderPaths(renderingConfig: BeRenderingConfig, oldOverlay: ControlFlowPathOverlay): ControlFlowPathOverlay = {
    val seg = renderingConfig.controlSegmentSize
    val curLineHeight = seg * minHeightInSegments
    val extraHeight = (curLineHeight - seg * minHeightInSegments).max(0)

    oldOverlay.lastPathByStatusAndType(PathStatus.OPEN, PathType.BASE) match {
      case Some((parentPath, parentIndex)) =>
        val updatedParentBuilder = parentPath.segments.last.curPath
          .verticalLineWithHeight(extraHeight / 2)
          .lineToRel(Dimension[Double](0, seg))
          .lineToRel(Dimension(3 * seg, 0))
          .lineToRel(Dimension(0, 3 * seg))

        val parentEndPos = updatedParentBuilder.current
        val updatedParent = parentPath.copy(
          curStatus = PathStatus.PAUSED,
          segments = parentPath.segments.init :+ parentPath.segments.last.copy(curPath = updatedParentBuilder)
        )

        val ifPath = {
          val curPath = SvgPathBuilder[Double](parentEndPos)
            .lineToRel(Dimension[Double](3 * seg, 3 * seg))
            .lineToRel(Dimension[Double](0, seg))
            .verticalLineWithHeight(extraHeight / 2)
          ControlFlowPath(PathStatus.OPEN, PathType.CONDITION_TRUE, List(ControlFlowPathSegment(curPath, SegmentType.TRUE_SEGMENT_ACTIVE)))
        }

        val elsePath = {
          val curPathRed = SvgPathBuilder[Double](parentEndPos)
            .lineToRel(Dimension[Double](-3 * seg, 3 * seg))
            .lineToRel(Dimension[Double](0, seg))
            .verticalLineWithHeight(extraHeight / 2)
          val curPathInactive = SvgPathBuilder[Double](curPathRed.current)
          ControlFlowPath(
            PathStatus.HANDLED,
            PathType.CONDITION_FALSE,
            List(
              ControlFlowPathSegment(curPathRed, SegmentType.FALSE_SEGMENT_ACTIVE),
              ControlFlowPathSegment(curPathInactive, SegmentType.FALSE_SEGMENT_INACTIVE)
            )
          )
        }

        oldOverlay
          .replacePath(parentIndex, updatedParent, setToHandled = false)
          .addPath(elsePath)
          .addPath(ifPath)

      case None => oldOverlay
    }
  }
}
