package interactionPlugins.blockEnvironment.programming.shapes.controlflow

import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.SvgPathBuilder
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.shapes.BeShape.BeShapePathBased
import interactionPlugins.blockEnvironment.programming.shapes.ShapeFactory

case object ControlFlowConnectorBackground extends BeShapePathBased {

  private val connectorSegmentSize: Double = 5

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = ShapeFactory.buildControlFlowShapeDown(bounds, connectorSegmentSize)

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = Dimension(0,0)

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = Dimension(0,0)

  override def displaySize(config: BeRenderingConfig): Dimension[Double] = Dimension[Double](connectorSegmentSize * 6, connectorSegmentSize * 3)
}
