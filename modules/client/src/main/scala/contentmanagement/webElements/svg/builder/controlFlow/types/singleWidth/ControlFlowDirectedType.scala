package contentmanagement.webElements.svg.builder.controlFlow.types.singleWidth

import contentmanagement.webElements.svg.builder.controlFlow.path.{ControlFlowPathOverlay, PathStatus, PathType}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.core.datastructures.geometry.Dimension

case class ControlFlowDirectedType(goesDown: Boolean, isActive: Boolean = false) extends ControlFlowTypeSingleWidth {

  override def minHeightInSegments: Int = 2

  override def renderPaths(renderingConfig: BeRenderingConfig, oldOverlay: ControlFlowPathOverlay): ControlFlowPathOverlay = {
    val seg = renderingConfig.controlSegmentSize
    val curLineHeight = seg * minHeightInSegments

    oldOverlay.changePathBuilderByStatusAndType(PathStatus.OPEN, PathType.BASE) { builder =>
      val fullArrowHeight: Int = (seg * 4).toInt
      val arrowNr = curLineHeight.toInt / fullArrowHeight

      var res = builder
        .moveToRel(Dimension(-5.0, 0.0))
        .horizontalLineWithWidth(10.0)
        .moveToRel(Dimension(-5.0, 0.0))

      var movedY: Double = 0

      for (_ <- 0 until arrowNr) {
        val arrowShapeHeight: Double = seg / 5.0 * 9.0
        val distToArrow = seg / 5.0 * 1.0
        val heightForLines = fullArrowHeight - arrowShapeHeight - distToArrow
        res = res
          .verticalLineWithHeight(heightForLines / 2)
          .moveToRel(Dimension(0, arrowShapeHeight + 2 * distToArrow))
          .verticalLineWithHeight(heightForLines / 2)
        movedY += heightForLines + arrowShapeHeight + 2 * distToArrow
      }

      val yToGo: Double = curLineHeight - movedY
      res
        .verticalLineWithHeight(yToGo)
        .moveToRel(Dimension(-5.0, 0.0))
        .horizontalLineWithWidth(10.0)
        .moveToRel(Dimension(-5.0, 0.0))
    }
  }
}
