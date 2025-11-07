package contentmanagement.webElements.svg.shapes.controlflow

import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.BeShape.BeShapePathBased
import contentmanagement.webElements.svg.shapes.ControlFlowShapeFactory
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

case class ControlFlowStopperBackground() extends BeShapePathBased {

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] =
    ControlFlowShapeFactory[Double](config).buildControlFlowStop(bounds)

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = Dimension(0,0)

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = Dimension(0,0)

  override def displaySize(config: BeRenderingConfig): Dimension[Double] = {
    Dimension[Double](config.controlSegmentSize * 6, config.controlSegmentSize * 2)
  }

}