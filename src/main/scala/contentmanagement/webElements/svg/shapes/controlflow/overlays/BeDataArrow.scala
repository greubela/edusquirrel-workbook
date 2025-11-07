package contentmanagement.webElements.svg.shapes.controlflow.overlays

import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.{BeShapeDecoration, ControlFlowShapeFactory}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

case class BeDataArrow(pointsLeft: Boolean = true) extends BeShapeDecoration  {

  override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = Dimension[Double](rendererConfig.controlSegmentSize / 5.0 * 8.0, rendererConfig.controlSegmentSize / 5.0 * 8.0)

  def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = {
    if(pointsLeft)    ControlFlowShapeFactory[Double](rendererConfig).dataArrowLeft(bounds)
    else ControlFlowShapeFactory[Double](rendererConfig).dataArrowRight(bounds)
  }


}