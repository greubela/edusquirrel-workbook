package contentmanagement.webElements.svg.shapes

import contentmanagement.model.geometry.Bounds
import contentmanagement.webElements.svg.builder.SvgPathBuilder

/**
  * Test-only accessors for invoking [[ShapeFactory]] helpers that are package-private.
  */
object TestShapeFactoryAccess {

  def duck(bounds: Bounds[Double]): SvgPathBuilder[Double] = ShapeFactory.buildDuckShape(bounds)

  def literal(bounds: Bounds[Double]): SvgPathBuilder[Double] = ShapeFactory.buildLiteralShape(bounds)
}
