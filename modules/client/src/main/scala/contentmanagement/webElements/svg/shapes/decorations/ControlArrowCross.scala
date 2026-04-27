package contentmanagement.webElements.svg.shapes.decorations

import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.{BeShapeDecoration, DecorationFactory}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

import com.raquo.laminar.api.L
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.{BeShapeDecoration, DecorationFactory}
import datastructures.core.geometry.{Dimension, Point}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

case class ControlArrowCross(pointsDown: Boolean = true, isActive: Boolean = true, cleanOrigin: Boolean = false) extends BeShapeDecoration {

  override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = Dimension(rendererConfig.controlSegmentSize, rendererConfig.controlSegmentSize)

  def getOverlayPath(rendererConfig: BeRenderingConfig, center: Point[Double]): SvgPathBuilder[Double] = {
    if (pointsDown) DecorationFactory[Double](rendererConfig).controlFlowCrossDown(center, cleanOrigin)
    else DecorationFactory[Double](rendererConfig).controlFlowCrossUp(center, cleanOrigin)
  }

  def getAmends(renderingConfig: BeRenderingConfig): Seq[L.Modifier[L.SvgElement]] =
    if (isActive) renderingConfig.amendFactory.activeDecorationElements
    else renderingConfig.amendFactory.inActiveDecorationElements
}
