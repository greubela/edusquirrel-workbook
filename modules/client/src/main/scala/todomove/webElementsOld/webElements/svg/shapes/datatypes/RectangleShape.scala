package todomove.webElementsOld.webElements.svg.shapes.datatypes

import it.evadid.core.datastructures.vectorShapes.blocks.{BlockPaths, FlowDecorationPaths}

import todomove.webElementsOld.webElements.svg.shapes.BeShape.BeShapePathBased
import it.evadid.core.datastructures.geometry.{Bounds, Dimension}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig


object RectangleShape extends BeShapePathBased {

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = BlockPaths.buildRectangle(bounds)

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = config.paddingSmall

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = config.paddingSmall

}

