package todomove.webElementsOld.webElements.svg.builder.controlFlow.types.doubleWidth

import todomove.webElementsOld.webElements.svg.shapes.BeShape.BeShapeContainerable
import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.builder.controlFlow.path.{ControlFlowPath, ControlFlowPathOverlay, ControlFlowPathSegment, PathStatus, PathType, SegmentType}
import todomove.webElementsOld.webElements.svg.shapes.controlflow.ControlFlowConnectorBackground

case class RepetitionUnionType() extends ControlFlowTypeDoubleWidth {

  override def minHeightInSegments: Int = 5

  override def backgroundShape: BeShapeContainerable = ControlFlowConnectorBackground(List((true, true), (true, false)))

  private def finishRepetitionStopPath(path: ControlFlowPath, seg: Double): (ControlFlowPath, Point[Double]) = {
    val newSegment = ControlFlowPathSegment(
      SvgPathBuilder[Double](path.segments.last.curPath.current)
        .verticalLineWithHeight(seg)
        .lineToRel(Dimension(seg / 2, seg / 2))
        .verticalLineWithHeight(seg * 1.5),
      SegmentType.FALSE_SEGMENT_ACTIVE
    )
    val updated = path.copy(curStatus = PathStatus.FINISHED, segments = path.segments :+ newSegment)
    (updated, newSegment.curPath.current)
  }

  private def combinePaths(firstPath: ControlFlowPath, secondPath: ControlFlowPath, seg: Double): ControlFlowPath = {
    val newSecondBuilder = secondPath.segments.last.curPath.lineToRel(Dimension(-seg, seg))
    val updatedSecond = secondPath.copy(segments = secondPath.segments.init :+ secondPath.segments.last.copy(curPath = newSecondBuilder))

    val combiningPath = SvgPathBuilder[Double](newSecondBuilder.current)
      .horizontalLineWithWidth(-2 * seg)
      .horizontalLineWithWidth(-1.5 * seg)
      .lineToRel(Dimension(-seg, -seg))

    val combiningSegment = ControlFlowPathSegment(combiningPath, SegmentType.RETURN_SEGMENT)

    updatedSecond.copy(
      curStatus = PathStatus.FINISHED,
      segments = updatedSecond.segments ++ List(combiningSegment) ++ firstPath.segments
    )
  }

  override def renderPaths(renderingConfig: BeRenderingConfig, oldOverlay: ControlFlowPathOverlay): ControlFlowPathOverlay = {
    val seg = renderingConfig.controlSegmentSize
    val curLineHeight = seg * minHeightInSegments
    val extraHeight = (curLineHeight - minHeightInSegments * seg).max(0)

    val finishedStop = oldOverlay.lastPathByStatusAndType(PathStatus.OPEN, PathType.CONDITION_FALSE).map {
      case (path, index) =>
        val (updated, endPosition) = finishRepetitionStopPath(path, seg)
        (updated, index, endPosition)
    }

    val withFinishedStop = finishedStop match {
      case Some((updated, index, _)) => oldOverlay.replacePath(index, updated, setToHandled = false)
      case None => oldOverlay
    }

    val withCombined = withFinishedStop.unionOpenPathsByType(PathType.CONDITION_FALSE, PathType.CONDITION_TRUE, setToHandled = false) { (firstPath, secondPath) =>
      combinePaths(firstPath, secondPath, seg)
    }

    finishedStop match {
      case Some((_, _, endPosition)) =>
        withCombined.changePathByStatusAndType(PathStatus.PAUSED, PathType.BASE, setToHandled = false) { path =>
          val updatedBuilder = path.segments.last.curPath
            .moveToAbs(endPosition)
            .lineToRel(Dimension(0, 2 * seg))
            .verticalLineWithHeight(extraHeight)
          path.copy(curStatus = PathStatus.HANDLED, segments = path.segments.init :+ path.segments.last.copy(curPath = updatedBuilder))
        }
      case None => withCombined
    }
  }
}
