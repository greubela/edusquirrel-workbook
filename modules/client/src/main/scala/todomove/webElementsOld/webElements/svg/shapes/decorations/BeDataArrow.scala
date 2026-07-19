package todomove.webElementsOld.webElements.svg.shapes.decorations

import com.raquo.laminar.api.L
import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.shapes.{BeShapeDecoration, DecorationFactory}

case class BeDataArrow(pointsLeft: Boolean = true) extends BeShapeDecoration {

  override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = Dimension[Double](rendererConfig.controlSegmentSize / 5.0 * 8.0, rendererConfig.controlSegmentSize / 5.0 * 8.0)

  def getOverlayPath(rendererConfig: BeRenderingConfig, center: Point[Double]): SvgPathBuilder[Double] = {
    if (pointsLeft) DecorationFactory[Double](rendererConfig).dataArrowLeft(center)
    else DecorationFactory[Double](rendererConfig).dataArrowRight(center)
  }

  def getAmends(renderingConfig: BeRenderingConfig): Seq[L.Modifier[L.SvgElement]] = renderingConfig.amendFactory.activeDecorationElements

}