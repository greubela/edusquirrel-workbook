package contentmanagement.webElements.svg.builder.controlFlow.types.singleWidth

import contentmanagement.model.geometry.Dimension
import contentmanagement.webElements.svg.builder.controlFlow.path.{ControlFlowPathOverlay, PathStatus, PathType}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

case class ControlFlowEmptyType() extends ControlFlowTypeSingleWidth {

  override def minHeightInSegments: Int = 1

  override def renderPaths(renderingConfig: BeRenderingConfig, oldOverlay: ControlFlowPathOverlay): ControlFlowPathOverlay = {
    val lineHeight = renderingConfig.controlSegmentSize * minHeightInSegments
    oldOverlay.changePathLastSegmentByStatusAndType(PathStatus.OPEN, PathType.BASE) { segment =>
      segment.copy(curPath = segment.curPath.moveToRel(Dimension(0, lineHeight)))
    }
  }
}
