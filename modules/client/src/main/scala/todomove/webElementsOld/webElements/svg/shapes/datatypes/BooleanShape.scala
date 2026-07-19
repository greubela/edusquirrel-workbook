package todomove.webElementsOld.webElements.svg.shapes.datatypes

import todomove.webElementsOld.webElements.svg.shapes.BeShape.{BeShapeAtomic, BeShapeContainerable, BeShapePathBased}
import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.AppSvgElement
import todomove.webElementsOld.webElements.svg.shapes.ShapeFactory


object BooleanShape extends BeShapePathBased {

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = ShapeFactory.buildBooleanShape(bounds)

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = config.paddingSmall.increaseSize(5, 0)

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = config.paddingSmall.increaseSize(5, 0)

}