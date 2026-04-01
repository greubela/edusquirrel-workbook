package contentmanagement.webElements.svg.shapes.decorations

import com.raquo.laminar.api.L
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.{BeShapeDecoration, DecorationFactory}
import datastructures.core.geometry.{Dimension, Point}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

case class BeDataArrow(pointsLeft: Boolean = true) extends BeShapeDecoration {

  override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = Dimension[Double](rendererConfig.controlSegmentSize / 5.0 * 8.0, rendererConfig.controlSegmentSize / 5.0 * 8.0)

  def getOverlayPath(rendererConfig: BeRenderingConfig, center: Point[Double]): SvgPathBuilder[Double] = {
    if (pointsLeft) DecorationFactory[Double](rendererConfig).dataArrowLeft(center)
    else DecorationFactory[Double](rendererConfig).dataArrowRight(center)
  }

  def getAmends(renderingConfig: BeRenderingConfig): Seq[L.Modifier[L.SvgElement]] = renderingConfig.amendFactory.activeDecorationElements

}