package todomove.webElementsOld.webElements.svg.shapes.decorations

import it.evadid.core.datastructures.vectorShapes.blocks.{BlockPaths, FlowDecorationPaths}

import com.raquo.laminar.api.L
import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.shapes.{BeShapeDecoration}

case class ControlArrowCross(pointsDown: Boolean = true, isActive: Boolean = true, cleanOrigin: Boolean = false) extends BeShapeDecoration derives upickle.default.ReadWriter {

  override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = Dimension(rendererConfig.controlSegmentSize, rendererConfig.controlSegmentSize)

  def getOverlayPath(rendererConfig: BeRenderingConfig, center: Point[Double]): SvgPathBuilder[Double] = {
    if (pointsDown) FlowDecorationPaths[Double](rendererConfig.controlSegmentSize).controlFlowCrossDown(center, cleanOrigin)
    else FlowDecorationPaths[Double](rendererConfig.controlSegmentSize).controlFlowCrossUp(center, cleanOrigin)
  }

  def getAmends(renderingConfig: BeRenderingConfig): Seq[L.Modifier[L.SvgElement]] =
    if (isActive) renderingConfig.amendFactory.activeDecorationElements
    else renderingConfig.amendFactory.inActiveDecorationElements
}
