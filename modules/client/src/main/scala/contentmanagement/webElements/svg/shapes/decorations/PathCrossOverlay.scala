package contentmanagement.webElements.svg.shapes.decorations

import com.raquo.laminar.api.L
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.BeShapeDecoration
import datastructures.core.geometry.{Dimension, Point}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

case class PathCrossOverlay() extends BeShapeDecoration {


  override def getOverlayPath(rendererConfig: BeRenderingConfig, centeredAt: Point[Double]): SvgPathBuilder[Double] = {
    val seg = rendererConfig.controlSegmentSize
    SvgPathBuilder(centeredAt)
      .addCenteredCircle(seg)
      .closePath()
  }

  override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = {
    val seg = rendererConfig.controlSegmentSize
    Dimension[Double](2 * seg, 2 * seg)
  }

  def getAmends(renderingConfig: BeRenderingConfig): Seq[L.Modifier[L.SvgElement]] = renderingConfig.amendFactory.crossSymbolControlFlowAmends

}
