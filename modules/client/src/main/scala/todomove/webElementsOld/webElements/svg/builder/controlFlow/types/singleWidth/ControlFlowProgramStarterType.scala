package todomove.webElementsOld.webElements.svg.builder.controlFlow.types.singleWidth

import todomove.webElementsOld.webElements.svg.shapes.BeShape.BeShapeContainerable
import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.builder.controlFlow.path.{ControlFlowPathOverlay, PathStatus, PathType, SegmentType}
import todomove.webElementsOld.webElements.svg.shapes.controlflow.ControlFlowStarterBackground

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
