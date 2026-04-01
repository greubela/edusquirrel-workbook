package contentmanagement.webElements.svg.shapes

import contentmanagement.webElements.svg.builder.SvgPathBuilder
import datastructures.core.geometry.Bounds

/**
  * Test-only accessors for invoking [[ShapeFactory]] helpers that are package-private.
  */
object TestShapeFactoryAccess {

  def duck(bounds: Bounds[Double]): SvgPathBuilder[Double] = ShapeFactory.buildDuckShape(bounds)

  def literal(bounds: Bounds[Double]): SvgPathBuilder[Double] = ShapeFactory.buildLiteralShape(bounds)
}
