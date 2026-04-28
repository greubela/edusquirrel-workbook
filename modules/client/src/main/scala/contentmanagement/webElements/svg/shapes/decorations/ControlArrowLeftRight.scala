package contentmanagement.webElements.svg.shapes.decorations

import com.raquo.laminar.api.L
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.{BeShapeDecoration, DecorationFactory}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.core.datastructures.geometry.{Dimension, Point}

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


