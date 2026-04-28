package contentmanagement.webElements.svg.shapes.controlflow

import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.BeShape.BeShapePathBased
import contentmanagement.webElements.svg.shapes.DecorationFactory
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.core.datastructures.geometry.{Bounds, Dimension}

case class ControlFlowStopperBackground() extends BeShapePathBased {

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] =
    DecorationFactory[Double](config).buildControlFlowStop(bounds)

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = Dimension(0,0)

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = Dimension(0,0)

  override def displaySize(config: BeRenderingConfig): Dimension[Double] = {
    Dimension[Double](config.controlSegmentSize * 6, config.controlSegmentSize * 2)
  }

}