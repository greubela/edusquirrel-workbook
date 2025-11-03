package contentmanagement.webElements.svg.shapes.controlflow

import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.{ControlFlowShapeFactory, ShapeFactory}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import contentmanagement.webElements.svg.shapes.BeShape.BeShapePathBased

case class ControlFlowConnectorBackground(connectorShapes: List[(Boolean, Boolean)]) extends BeShapePathBased {

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] =
    ControlFlowShapeFactory[Double](config).buildControlFlowBackgroundMultipleSize(bounds, connectorShapes)

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = Dimension(0,0)

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = Dimension(0,0)

  override def displaySize(config: BeRenderingConfig): Dimension[Double] = Dimension[Double](config.controlSegmentSize * 6, config.controlSegmentSize * 3)
}
