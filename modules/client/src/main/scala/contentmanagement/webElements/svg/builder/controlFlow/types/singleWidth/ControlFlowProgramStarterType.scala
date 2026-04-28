package contentmanagement.webElements.svg.builder.controlFlow.types.singleWidth

import contentmanagement.webElements.svg.builder.controlFlow.path.{ControlFlowPathOverlay, PathStatus, PathType, SegmentType}
import contentmanagement.webElements.svg.shapes.BeShape.BeShapeContainerable
import contentmanagement.webElements.svg.shapes.controlflow.ControlFlowStarterBackground
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.core.datastructures.geometry.{Dimension, Point}

case class ControlFlowProgramStarterType() extends ControlFlowTypeSingleWidth {

  override def backgroundShape: BeShapeContainerable = ControlFlowStarterBackground()

  override def minHeightInSegments: Int = 3

  override def renderPaths(renderingConfig: BeRenderingConfig, oldOverlay: ControlFlowPathOverlay): ControlFlowPathOverlay = {
    val actualLineHeight = renderingConfig.controlSegmentSize * minHeightInSegments + renderingConfig.controlSegmentSize
    oldOverlay
      .startNewPath(Point(0, 0), PathType.BASE, SegmentType.BASE)
      .changePathBuilderByStatusAndType(PathStatus.OPEN, PathType.BASE)(_
        .moveToRel(Dimension(3 * renderingConfig.controlSegmentSize, actualLineHeight / 2))
        .lineToRel(Dimension(0, actualLineHeight / 2))
      )
  }
}
