package contentmanagement.webElements.svg.shapes.controlflow.overlays

import com.raquo.laminar.api.L
import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.*
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

case class BeControlArrow() extends BeShapeDecoration {

  override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = Dimension(rendererConfig.controlSegmentSize / 5.0 * 6.0, rendererConfig.controlSegmentSize / 5.0 * 8.0)
  
  def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] =
    ControlFlowShapeFactory[Double](rendererConfig).controlArrowDown(bounds)

}
