package contentmanagement.webElements.svg.shapes.controlflow.singleWidth

import contentmanagement.webElements.svg.shapes.decorations.ControlArrowUpDown
import datastructures.core.geometry.{Dimension, Point}
import interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.ControlFlowPath

case class ControlFlowDownUp(isActive: Boolean = false) extends ControlFlowShapeSingleWidth {

  override def minHeightInSegments: Int = 2

  val downFlow = ControlFlowDirected(true, isActive)
  val upFlow = ControlFlowDirected(true, isActive)

  override def renderControlFlow(cf: ControlFlowOverlayBuilder, renderingInfo: RenderingInformation, centerPoint: Point[Double], curLineHeight: Double): ControlFlowOverlayBuilder = {
    renderControlFlowWithoutArrows(cf, renderingInfo, centerPoint, curLineHeight)
  }


  def renderControlFlowWithoutArrows(cf: ControlFlowOverlayBuilder, renderingInfo: RenderingInformation, centerPoint: Point[Double], curLineHeight: Double): ControlFlowOverlayBuilder = {
    cf
      .changeFirstOpenPath(_.changeLastPathBuilder(_.verticalLineWithHeight(curLineHeight)))
      .changeFirstOpenPath(_.changeLastPathBuilder(_.verticalLineWithHeight(curLineHeight)))
  }

  def renderControlFlowWithArrows(cf: ControlFlowOverlayBuilder, renderingInfo: RenderingInformation, centerPoint: Point[Double], curLineHeight: Double): ControlFlowOverlayBuilder = {
    var res = cf
    // left
    val (handledDown, arrowPositionsDown): (ControlFlowPath, List[Point[Double]]) = downFlow.handleParentPath(res.firstOpenPath, renderingInfo.renderingConfig, centerPoint, curLineHeight)
    res = res.changeFirstOpenPath(_ => handledDown)
    // right
    val (handledUp, arrowPositionsUp): (ControlFlowPath, List[Point[Double]]) = upFlow.handleParentPath(res.firstOpenPath, renderingInfo.renderingConfig, centerPoint, curLineHeight)
    res = res.changeFirstOpenPath(_ => handledUp)
    // arrows left
    for (curArrowCenter <- arrowPositionsDown) {
      val offset = Dimension[Double](-renderingInfo.renderingConfig.controlSegmentSize / 2, 0)
      res = res.addDecoration(ControlArrowUpDown(true, isActive), curArrowCenter.moveWithDimension(offset))
    }
    // arrows right
    for (curArrowCenter <- arrowPositionsUp) {
      val offset = Dimension[Double](renderingInfo.renderingConfig.controlSegmentSize / 2, 0)
      res = res.addDecoration(ControlArrowUpDown(false, isActive), curArrowCenter.moveWithDimension(offset))
    }
    res
  }
}
