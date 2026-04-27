package contentmanagement.webElements.svg.shapes.decorations

import com.raquo.laminar.api.L
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.BeShapeDecoration
import datastructures.core.geometry.{Dimension, Point}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

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



