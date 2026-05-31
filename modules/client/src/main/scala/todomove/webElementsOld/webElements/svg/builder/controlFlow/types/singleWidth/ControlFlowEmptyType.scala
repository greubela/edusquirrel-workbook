package todomove.webElementsOld.webElements.svg.builder.controlFlow.types.singleWidth

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.builder.controlFlow.path.{ControlFlowPathOverlay, PathStatus, PathType}

case class ControlFlowEmptyType() extends ControlFlowTypeSingleWidth {

  override def minHeightInSegments: Int = 1

  override def renderPaths(renderingConfig: BeRenderingConfig, oldOverlay: ControlFlowPathOverlay): ControlFlowPathOverlay = {
    val lineHeight = renderingConfig.controlSegmentSize * minHeightInSegments
    oldOverlay.changePathLastSegmentByStatusAndType(PathStatus.OPEN, PathType.BASE) { segment =>
      segment.copy(curPath = segment.curPath.moveToRel(Dimension(0, lineHeight)))
    }
  }
}
