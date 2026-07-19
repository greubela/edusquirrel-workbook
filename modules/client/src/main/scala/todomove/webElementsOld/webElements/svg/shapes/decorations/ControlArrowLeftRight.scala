package todomove.webElementsOld.webElements.svg.shapes.decorations

import com.raquo.laminar.api.L
import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.shapes.{BeShapeDecoration, DecorationFactory}

case class ControlArrowLeftRight(pointsLeft: Boolean = true, isActive: Boolean = true) extends BeShapeDecoration {


  override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = Dimension(rendererConfig.controlSegmentSize / 5.0 * 6.0, rendererConfig.controlSegmentSize / 5.0 * 8.0)

  def getOverlayPath(rendererConfig: BeRenderingConfig, center: Point[Double]): SvgPathBuilder[Double] = {
    if (pointsLeft) DecorationFactory[Double](rendererConfig).controlArrowLeft(center)
    else DecorationFactory[Double](rendererConfig).controlArrowRight(center)
  }

  def getAmends(renderingConfig: BeRenderingConfig): Seq[L.Modifier[L.SvgElement]] =
    if (isActive) renderingConfig.amendFactory.activeDecorationElements
    else renderingConfig.amendFactory.inActiveDecorationElements
}


