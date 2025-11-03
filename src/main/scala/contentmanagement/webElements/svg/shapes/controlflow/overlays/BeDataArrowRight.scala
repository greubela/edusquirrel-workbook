package contentmanagement.webElements.svg.shapes.controlflow.overlays

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg
import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}
import contentmanagement.webElements.svg.shapes.*
import interactionPlugins.blockEnvironment.config.BeRenderingConfig


case class BeDataArrowRight() extends BeShapeDecoration  {

  override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = Dimension[Double](rendererConfig.controlSegmentSize / 5.0 * 8.0, rendererConfig.controlSegmentSize / 5.0 * 8.0)

  def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] =
    ControlFlowShapeFactory[Double](rendererConfig).dataArrowRight(bounds)
  
  
}
