package todomove.webElementsOld.webElements.svg.shapes

import todomove.webElementsOld.webElements.svg.builder.SvgPathBuilder
import it.evadid.core.datastructures.geometry.Bounds

/**
  * Test-only accessors for invoking [[ShapeFactory]] helpers that are package-private.
  */
object TestShapeFactoryAccess {

  def duck(bounds: Bounds[Double]): SvgPathBuilder[Double] = ShapeFactory.buildDuckShape(bounds)

  def literal(bounds: Bounds[Double]): SvgPathBuilder[Double] = ShapeFactory.buildLiteralShape(bounds)
}
