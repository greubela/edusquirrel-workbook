package todomove.webElementsOld.webElements.svg.shapes.decorations

import com.raquo.laminar.api.L
import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.builder.SvgPathBuilder
import todomove.webElementsOld.webElements.svg.shapes.BeShapeDecoration

case class PathSplitOverlay() extends BeShapeDecoration {

  override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = Dimension(rendererConfig.controlSegmentSize * 2, rendererConfig.controlSegmentSize * 2)

  def getOverlayPath(rendererConfig: BeRenderingConfig, center: Point[Double]): SvgPathBuilder[Double] = {
    val seg = rendererConfig.controlSegmentSize
    SvgPathBuilder(center)
      .moveToRel(Dimension(0, -seg))
      .lineToRel(Dimension(seg, seg))
      .lineToRel(Dimension(-seg, seg))
      .lineToRel(Dimension(-seg, -seg))
      .lineToRel(Dimension(seg, -seg))
      .closePath()
  }

  def getAmends(renderingConfig: BeRenderingConfig): Seq[L.Modifier[L.SvgElement]] = renderingConfig.amendFactory.splitSymbolControlFlowAmends

}



