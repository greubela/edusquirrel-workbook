package contentmanagement.webElements.svg.shapes.special

import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.BeShape.BeShapePathBased
import contentmanagement.webElements.svg.shapes.{BeShapeDecoration, ShapeFactory}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

case class CommandShape() extends BeShapePathBased {

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = ShapeFactory.buildCommandShape(bounds, config.controlSegmentSize)

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = config.paddingSmall.increaseSize(Dimension(config.controlSegmentSize , 0))

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = config.paddingSmall.increaseSize(Dimension(config.controlSegmentSize , 0))

}