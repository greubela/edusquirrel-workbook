package contentmanagement.webElements.svg.shapes.datatypes

import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.ShapeFactory
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import contentmanagement.webElements.svg.shapes.BeShape.BeShapePathBased


// todo add control flow shape elements as specific elements. distinguish between unit element and control flow (control-flow = connectors, unit = ..?)
object BeStarterShape extends BeShapePathBased{

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = ShapeFactory.buildStarterShape(bounds)

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = Dimension(0, 20)

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = Dimension(0, 20)
}
