package todomove.webElementsOld.webElements.svg.builder.controlFlow.types.singleWidth

import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.builder.controlFlow.path.{ControlFlowPathOverlay, PathStatus, PathType}

case class ControlFlowDownUpType(isActive: Boolean = false) extends ControlFlowTypeSingleWidth {

  override def minHeightInSegments: Int = 2

  override def renderPaths(renderingConfig: BeRenderingConfig, oldOverlay: ControlFlowPathOverlay): ControlFlowPathOverlay = {
    val curLineHeight = renderingConfig.controlSegmentSize * minHeightInSegments
    oldOverlay
      .changePathBuilderByStatusAndType(PathStatus.OPEN, PathType.BASE)(_.verticalLineWithHeight(curLineHeight))
      .changePathBuilderByStatusAndType(PathStatus.OPEN, PathType.BASE)(_.verticalLineWithHeight(curLineHeight))
  }
}
