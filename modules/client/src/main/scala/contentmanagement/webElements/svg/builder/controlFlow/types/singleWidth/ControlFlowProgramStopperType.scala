package contentmanagement.webElements.svg.builder.controlFlow.types.singleWidth

import contentmanagement.webElements.svg.builder.controlFlow.path.{ControlFlowPathOverlay, PathStatus, PathType}
import contentmanagement.webElements.svg.shapes.BeShape.BeShapeContainerable
import contentmanagement.webElements.svg.shapes.controlflow.ControlFlowStopperBackground
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.core.datastructures.geometry.Dimension

case class ControlFlowProgramStopperType() extends ControlFlowTypeSingleWidth {

  override def backgroundShape: BeShapeContainerable = ControlFlowStopperBackground()

  override def minHeightInSegments: Int = 3

  override def renderPaths(renderingConfig: BeRenderingConfig, oldOverlay: ControlFlowPathOverlay): ControlFlowPathOverlay = {
    val seg = renderingConfig.controlSegmentSize
    val actualLineHeight = seg * minHeightInSegments - seg / 2

    oldOverlay.changePathByStatusAndType(PathStatus.OPEN, PathType.BASE, setToHandled = false) { path =>
      val updated = path.segments.last.curPath
        .lineToRel(Dimension(0, actualLineHeight / 2))
        .moveToRel(Dimension(-seg, 0))
        .horizontalLineWithWidth(2 * seg)
      path.copy(curStatus = PathStatus.FINISHED, segments = path.segments.init :+ path.segments.last.copy(curPath = updated))
    }
  }
}
