package todomove.webElementsOld.webElements.svg.shapes.decorations

import com.raquo.laminar.api.L
import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.builder.SvgPathBuilder
import todomove.webElementsOld.webElements.svg.shapes.BeShapeDecoration

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
