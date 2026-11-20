package todomove.webElementsOld.webElements.svg.shapes.decorations

import it.evadid.core.datastructures.vectorShapes.blocks.{BlockPaths, FlowDecorationPaths}

import com.raquo.laminar.api.L
import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.shapes.{BeShapeDecoration}

case class BeDataArrow(pointsLeft: Boolean = true) extends BeShapeDecoration derives upickle.default.ReadWriter {

  override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = Dimension[Double](rendererConfig.controlSegmentSize / 5.0 * 8.0, rendererConfig.controlSegmentSize / 5.0 * 8.0)

  def getOverlayPath(rendererConfig: BeRenderingConfig, center: Point[Double]): SvgPathBuilder[Double] = {
    if (pointsLeft) FlowDecorationPaths[Double](rendererConfig.controlSegmentSize).dataArrowLeft(center)
    else FlowDecorationPaths[Double](rendererConfig.controlSegmentSize).dataArrowRight(center)
  }

  def getAmends(renderingConfig: BeRenderingConfig): Seq[L.Modifier[L.SvgElement]] = renderingConfig.amendFactory.activeDecorationElements

}