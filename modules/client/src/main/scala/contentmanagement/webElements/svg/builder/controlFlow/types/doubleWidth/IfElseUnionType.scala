package contentmanagement.webElements.svg.builder.controlFlow.types.doubleWidth

import contentmanagement.webElements.svg.builder.controlFlow.path.{ControlFlowPathOverlay, PathStatus, PathType}
import contentmanagement.webElements.svg.shapes.BeShape.BeShapeContainerable
import contentmanagement.webElements.svg.shapes.controlflow.ControlFlowConnectorBackground
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.core.datastructures.geometry.Dimension

case class IfElseUnionType() extends ControlFlowTypeDoubleWidth {

  override def minHeightInSegments: Int = 7

  override def backgroundShape: BeShapeContainerable =
    ControlFlowConnectorBackground(List((true, true), (true, false)))

  override def renderPaths(renderingConfig: BeRenderingConfig, oldOverlay: ControlFlowPathOverlay): ControlFlowPathOverlay = {
    val seg = renderingConfig.controlSegmentSize
    val curLineHeight = seg * minHeightInSegments
    val extraHeight = (curLineHeight - seg * minHeightInSegments).max(0)

    val updatedTrue = oldOverlay.lastPathByStatusAndType(PathStatus.OPEN, PathType.CONDITION_TRUE).map { case (path, index) =>
      val updatedBuilder = path.segments.last.curPath.lineToRel(Dimension(3 * seg, 3 * seg))
      val updatedPath = path.copy(curStatus = PathStatus.FINISHED, segments = path.segments.init :+ path.segments.last.copy(curPath = updatedBuilder))
      (updatedPath, index, updatedBuilder.current)
    }

    val updatedFalse = oldOverlay.lastPathByStatusAndType(PathStatus.OPEN, PathType.CONDITION_FALSE).map { case (path, index) =>
      val updatedBuilder = path.segments.last.curPath.lineToRel(Dimension(-3 * seg, 3 * seg))
      val updatedPath = path.copy(curStatus = PathStatus.FINISHED, segments = path.segments.init :+ path.segments.last.copy(curPath = updatedBuilder))
      (updatedPath, index, updatedBuilder.current)
    }

    val withFinishedPaths = (updatedTrue, updatedFalse) match {
      case (Some((truePath, trueIndex, _)), Some((falsePath, falseIndex, _))) =>
        oldOverlay
          .replacePath(trueIndex, truePath, setToHandled = false)
          .replacePath(falseIndex, falsePath, setToHandled = false)
      case (Some((truePath, trueIndex, _)), None) =>
        oldOverlay.replacePath(trueIndex, truePath, setToHandled = false)
      case (None, Some((falsePath, falseIndex, _))) =>
        oldOverlay.replacePath(falseIndex, falsePath, setToHandled = false)
      case _ => oldOverlay
    }

    updatedTrue match {
      case Some((_, _, parentEndPos)) =>
        withFinishedPaths.changePathByStatusAndType(PathStatus.PAUSED, PathType.BASE, setToHandled = false) { path =>
          val updatedBuilder = path.segments.last.curPath
            .moveToAbs(parentEndPos)
            .lineToRel(Dimension(0, 2 * seg))
            .horizontalLineWithWidth(-3 * seg)
            .verticalLineWithHeight(2 * seg)
            .verticalLineWithHeight(extraHeight)
          path.copy(curStatus = PathStatus.HANDLED, segments = path.segments.init :+ path.segments.last.copy(curPath = updatedBuilder))
        }
      case None => withFinishedPaths
    }
  }
}
