package contentmanagement.webElements.svg.shapes.controlflow

import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.{DecorationFactory, ShapeFactory}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import contentmanagement.webElements.svg.shapes.BeShape.BeShapePathBased
import it.evadid.core.datastructures.geometry.{Bounds, Dimension}

case class ControlFlowConnectorBackground(connectorShapes: List[(Boolean, Boolean)], commandBracket: Boolean = false) extends BeShapePathBased {

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] =
    DecorationFactory[Double](config).buildControlFlowBackgroundMultipleSize(bounds, connectorShapes, commandBracket)

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = Dimension(0,0)

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = Dimension(0,0)

  override def displaySize(config: BeRenderingConfig): Dimension[Double] = Dimension[Double](config.controlSegmentSize * 6, config.controlSegmentSize * 3)
}
