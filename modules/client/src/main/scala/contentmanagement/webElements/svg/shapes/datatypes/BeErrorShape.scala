package contentmanagement.webElements.svg.shapes.datatypes

import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import contentmanagement.webElements.svg.shapes.BeShape.{BeShapeAtomic, BeShapeContainerable, BeShapePathBased}
import contentmanagement.webElements.svg.shapes.{AmendedShape, BeShape, ShapeFactory}
import datastructures.core.geometry.{Bounds, Dimension}

case object BeErrorShape extends BeShapePathBased {

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = ???

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = Dimension(0, 20)

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = Dimension(0, 20)
}
