package contentmanagement.webElements.svg.shapes.controlflow.decorations

import com.raquo.laminar.api.L
import contentmanagement.model.geometry.{Dimension, Point}
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.BeShapeDecoration
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

case class PathUnionOverlay() extends BeShapeDecoration {

  override def getOverlayPath(rendererConfig: BeRenderingConfig, centeredAt: Point[Double]): SvgPathBuilder[Double] = {
    val seg = rendererConfig.controlSegmentSize
    SvgPathBuilder(centeredAt)
      .addCenteredCircle(seg)
      .closePath()
  }

  override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = {
    val seg = rendererConfig.controlSegmentSize
    Dimension[Double](2*seg, 2*seg)
  }


  def getAmends(renderingConfig: BeRenderingConfig): Seq[L.Modifier[L.SvgElement]] = renderingConfig.amendFactory.unionSymbolControlFlowAmends
}
